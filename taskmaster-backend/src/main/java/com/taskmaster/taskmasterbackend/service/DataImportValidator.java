package com.taskmaster.taskmasterbackend.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.dto.transfer.DependencyData;
import com.taskmaster.taskmasterbackend.dto.transfer.ProjectData;
import com.taskmaster.taskmasterbackend.dto.transfer.TaskData;
import com.taskmaster.taskmasterbackend.dto.transfer.WorkLogData;
import com.taskmaster.taskmasterbackend.exception.BusinessException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee y valida por completo un fichero de importación de datos antes de escribir nada.
 *
 * <p>Es una clase pura (sin Spring ni base de datos), así que se prueba directamente. La lectura
 * es estricta: propiedades desconocidas o duplicadas, enums desconocidos o numéricos y contenido
 * tras el JSON se rechazan. Antes de crear ningún objeto, un preanálisis en streaming cuenta
 * objetos y anidamiento para que un fichero fabricado (por ejemplo, millones de {@code {}})
 * no agote la memoria. Todos los errores son {@link BusinessException} en español con la ruta
 * del fallo (p. ej. {@code projects[0].tasks[2].status}), sin nombres de clases ni el valor
 * recibido.</p>
 *
 * @author Carlos
 */
public class DataImportValidator {

    /** Máximo de objetos JSON del fichero, contados antes de mapearlo. */
    static final int MAX_OBJECTS = 200_000;
    /** Máximo de proyectos. */
    static final int MAX_PROJECTS = 1_000;
    /** Máximo de tareas, subtareas incluidas. */
    static final int MAX_TASKS = 20_000;
    /** Máximo de registros de trabajo. */
    static final int MAX_WORKLOGS = 100_000;
    /** Máximo de dependencias. */
    static final int MAX_DEPENDENCIES = 50_000;
    /** Máximo de niveles de subtareas. */
    static final int MAX_DEPTH = 50;
    /** Máximo de caracteres del nombre de un proyecto y del título de una tarea. */
    static final int MAX_NAME = 500;
    /** Máximo de caracteres de las descripciones y las notas. */
    static final int MAX_TEXT = 10_000;
    /** Máximo de caracteres de una referencia local. */
    static final int MAX_REF = 64;

    /**
     * Tope de la duración estimada: de cordura y holgado, porque la API y la interfaz solo exigen
     * que sea al menos 0,1 y no fijan máximo.
     */
    private static final BigDecimal MAX_DURATION = new BigDecimal("1000000");
    /** Máximo de decimales de duraciones y horas: la API y la interfaz no limitan la escala. */
    private static final int MAX_SCALE = 6;
    private static final BigDecimal MIN_HOURS = new BigDecimal("0.1");
    private static final BigDecimal MAX_HOURS = new BigDecimal("24");

    private final ObjectMapper objectMapper;
    private final ObjectReader reader;
    private final int maxObjects;
    private final int maxProjects;
    private final int maxTasks;
    private final int maxWorkLogs;
    private final int maxDependencies;
    private final int maxDepth;
    private final int maxName;
    private final int maxText;

    /**
     * Crea un validador con los límites de producción.
     *
     * @param objectMapper mapper con soporte de fechas (el de Spring Boot); no se modifica,
     *                     el lector estricto se deriva de él
     */
    public DataImportValidator(ObjectMapper objectMapper) {
        this(objectMapper, MAX_OBJECTS, MAX_PROJECTS, MAX_TASKS, MAX_WORKLOGS, MAX_DEPENDENCIES,
                MAX_DEPTH, MAX_NAME, MAX_TEXT);
    }

    /** Constructor con límites propios, para los tests. */
    DataImportValidator(ObjectMapper objectMapper, int maxObjects, int maxProjects, int maxTasks,
                        int maxWorkLogs, int maxDependencies, int maxDepth, int maxName, int maxText) {
        this.objectMapper = objectMapper;
        this.maxObjects = maxObjects;
        this.maxProjects = maxProjects;
        this.maxTasks = maxTasks;
        this.maxWorkLogs = maxWorkLogs;
        this.maxDependencies = maxDependencies;
        this.maxDepth = maxDepth;
        this.maxName = maxName;
        this.maxText = maxText;
        this.reader = objectMapper.readerFor(DataExportFile.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    // -------------------------------------------------------------------------
    // Lectura
    // -------------------------------------------------------------------------

    /**
     * Lee el fichero: preanálisis en streaming y mapeo estricto a {@link DataExportFile}.
     *
     * @param bytes contenido del fichero
     * @return el fichero leído, todavía sin validar semánticamente
     * @throws BusinessException si no es un JSON válido, es demasiado grande o anidado, o
     *                           tiene propiedades o valores que no corresponden al formato
     */
    public DataExportFile parse(byte[] bytes) {
        prescan(bytes);
        DataExportFile file;
        try {
            file = reader.readValue(bytes);
        } catch (JsonMappingException e) {
            String path = path(e);
            throw new BusinessException(path.isEmpty()
                    ? "El archivo no tiene el formato esperado"
                    : "Valor no válido en " + path);
        } catch (JsonProcessingException e) {
            throw new BusinessException(messageOf(e));
        } catch (IOException e) {
            throw new BusinessException("El archivo no es un JSON válido");
        }
        if (file == null) {
            throw new BusinessException("El archivo no es una exportación de TaskMaster");
        }
        return file;
    }

    /** Recorre los tokens sin crear objetos: cuenta objetos y anidamiento y detecta claves repetidas. */
    private void prescan(byte[] bytes) {
        int maxNesting = 2 * maxDepth + 8;
        int objects = 0;
        int depth = 0;
        try (JsonParser parser = objectMapper.getFactory().createParser(bytes)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    if (token == JsonToken.START_OBJECT && ++objects > maxObjects) {
                        throw new BusinessException("El archivo contiene demasiados elementos");
                    }
                    if (++depth > maxNesting) {
                        throw new BusinessException("El archivo está demasiado anidado");
                    }
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    depth--;
                }
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(messageOf(e));
        } catch (IOException e) {
            throw new BusinessException("El archivo no es un JSON válido");
        }
    }

    /** Mensaje en español para un fallo de lectura, sin exponer el texto de Jackson. */
    private static String messageOf(JsonProcessingException e) {
        if (e instanceof JsonParseException && e.getOriginalMessage() != null
                && e.getOriginalMessage().startsWith("Duplicate field")) {
            return "El archivo contiene una propiedad repetida";
        }
        return "El archivo no es un JSON válido";
    }

    /** Ruta del fallo con la forma {@code projects[0].tasks[2].status}. */
    private static String path(JsonMappingException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : e.getPath()) {
            if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            } else if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            }
        }
        return path.toString();
    }

    // -------------------------------------------------------------------------
    // Validación semántica
    // -------------------------------------------------------------------------

    /** Contadores y referencias de una pasada de validación (la clase en sí no guarda estado). */
    private static final class Run {
        int tasks;
        int workLogs;
        final Set<String> refs = new HashSet<>();
    }

    /**
     * Valida el fichero entero y lanza el primer error que encuentre.
     *
     * @param file fichero ya leído con {@link #parse(byte[])}
     * @throws BusinessException con la ruta del primer fallo
     */
    public void validate(DataExportFile file) {
        if (!DataExportFile.FORMAT.equals(file.format())) {
            throw new BusinessException("El archivo no es una exportación de TaskMaster");
        }
        if (file.version() == null || file.version() != DataExportFile.VERSION) {
            throw new BusinessException("Versión del archivo no compatible: se esperaba la "
                    + DataExportFile.VERSION);
        }

        List<ProjectData> projects = orEmpty(file.projects());
        List<TaskData> tasks = orEmpty(file.tasks());
        List<DependencyData> dependencies = orEmpty(file.dependencies());
        if (projects.isEmpty() && tasks.isEmpty()) {
            throw new BusinessException("El archivo no contiene proyectos ni tareas");
        }
        if (projects.size() > maxProjects) {
            throw fail("projects", "hay más de " + maxProjects + " proyectos");
        }
        if (dependencies.size() > maxDependencies) {
            throw fail("dependencies", "hay más de " + maxDependencies + " dependencias");
        }

        Run run = new Run();
        for (int i = 0; i < projects.size(); i++) {
            validateProject(projects.get(i), "projects[" + i + "]", run);
        }
        for (int i = 0; i < tasks.size(); i++) {
            validateTask(tasks.get(i), "tasks[" + i + "]", 0, run);
        }
        validateDependencies(dependencies, run.refs);
    }

    private void validateProject(ProjectData project, String path, Run run) {
        if (project == null) {
            throw fail(path, "el elemento está vacío");
        }
        checkName(project.name(), path + ".name");
        if (project.category() == null) {
            throw fail(path + ".category", "es obligatorio");
        }
        checkText(project.description(), path + ".description");
        checkDuration(project.estimatedDuration(), path + ".estimatedDuration");

        List<TaskData> tasks = orEmpty(project.tasks());
        for (int i = 0; i < tasks.size(); i++) {
            validateTask(tasks.get(i), path + ".tasks[" + i + "]", 0, run);
        }
    }

    private void validateTask(TaskData task, String path, int depth, Run run) {
        if (task == null) {
            throw fail(path, "el elemento está vacío");
        }
        if (depth > maxDepth) {
            throw fail(path, "hay más de " + maxDepth + " niveles de subtareas");
        }
        if (++run.tasks > maxTasks) {
            throw fail(path, "hay más de " + maxTasks + " tareas");
        }
        checkName(task.title(), path + ".title");
        checkText(task.description(), path + ".description");
        checkDate(task.dueDate(), path + ".dueDate");
        checkDuration(task.estimatedDuration(), path + ".estimatedDuration");
        checkRef(task.ref(), path + ".ref", run);

        List<WorkLogData> workLogs = orEmpty(task.workLogs());
        for (int i = 0; i < workLogs.size(); i++) {
            if (++run.workLogs > maxWorkLogs) {
                throw fail(path + ".workLogs[" + i + "]", "hay más de " + maxWorkLogs + " registros de trabajo");
            }
            validateWorkLog(workLogs.get(i), path + ".workLogs[" + i + "]");
        }

        List<TaskData> subtasks = orEmpty(task.subtasks());
        for (int i = 0; i < subtasks.size(); i++) {
            validateTask(subtasks.get(i), path + ".subtasks[" + i + "]", depth + 1, run);
        }
    }

    private void validateWorkLog(WorkLogData workLog, String path) {
        if (workLog == null) {
            throw fail(path, "el elemento está vacío");
        }
        if (workLog.date() == null) {
            throw fail(path + ".date", "es obligatorio");
        }
        checkDate(workLog.date(), path + ".date");
        if (workLog.activityType() == null) {
            throw fail(path + ".activityType", "es obligatorio");
        }
        BigDecimal hours = workLog.hours();
        if (hours == null) {
            throw fail(path + ".hours", "es obligatorio");
        }
        if (hours.compareTo(MIN_HOURS) < 0 || hours.compareTo(MAX_HOURS) > 0) {
            throw fail(path + ".hours", "debe estar entre 0,1 y 24 horas");
        }
        checkScale(hours, path + ".hours");
        checkText(workLog.note(), path + ".note");
    }

    private void validateDependencies(List<DependencyData> dependencies, Set<String> refs) {
        Map<String, Set<String>> edges = new HashMap<>();
        for (int i = 0; i < dependencies.size(); i++) {
            String path = "dependencies[" + i + "]";
            DependencyData dependency = dependencies.get(i);
            if (dependency == null) {
                throw fail(path, "el elemento está vacío");
            }
            String task = dependency.task();
            String dependsOn = dependency.dependsOn();
            if (task == null || task.isBlank()) {
                throw fail(path + ".task", "es obligatorio");
            }
            if (dependsOn == null || dependsOn.isBlank()) {
                throw fail(path + ".dependsOn", "es obligatorio");
            }
            if (!refs.contains(task)) {
                throw fail(path + ".task", "no corresponde a ninguna tarea del archivo");
            }
            if (!refs.contains(dependsOn)) {
                throw fail(path + ".dependsOn", "no corresponde a ninguna tarea del archivo");
            }
            if (task.equals(dependsOn)) {
                throw fail(path, "una tarea no puede depender de sí misma");
            }
            // Las dependencias repetidas se ignoran: es un conjunto
            edges.computeIfAbsent(task, k -> new LinkedHashSet<>()).add(dependsOn);
        }
        if (hasCycle(edges)) {
            throw new BusinessException("Las dependencias forman un ciclo");
        }
    }

    /** Ordenación topológica (Kahn) sobre el grafo del fichero: sobran nodos si hay un ciclo. */
    private static boolean hasCycle(Map<String, Set<String>> edges) {
        Map<String, Integer> incoming = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            incoming.putIfAbsent(entry.getKey(), 0);
            for (String target : entry.getValue()) {
                incoming.merge(target, 1, Integer::sum);
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        incoming.forEach((node, count) -> {
            if (count == 0) {
                ready.add(node);
            }
        });
        int processed = 0;
        while (!ready.isEmpty()) {
            String node = ready.poll();
            processed++;
            for (String target : edges.getOrDefault(node, Set.of())) {
                if (incoming.merge(target, -1, Integer::sum) == 0) {
                    ready.add(target);
                }
            }
        }
        return processed < incoming.size();
    }

    // -------------------------------------------------------------------------
    // Comprobaciones de campo
    // -------------------------------------------------------------------------

    private void checkName(String value, String path) {
        if (value == null || value.strip().isEmpty()) {
            throw fail(path, "es obligatorio");
        }
        if (value.length() > maxName) {
            throw fail(path, "supera los " + maxName + " caracteres");
        }
    }

    private void checkText(String value, String path) {
        if (value != null && value.length() > maxText) {
            throw fail(path, "supera los " + maxText + " caracteres");
        }
    }

    private void checkRef(String ref, String path, Run run) {
        if (ref == null) {
            return;
        }
        if (ref.isBlank() || ref.length() > MAX_REF) {
            throw fail(path, "debe tener entre 1 y " + MAX_REF + " caracteres");
        }
        if (!run.refs.add(ref)) {
            throw fail(path, "la referencia está repetida");
        }
    }

    private static void checkDate(LocalDate date, String path) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw fail(path, "la fecha está fuera de rango");
        }
    }

    private static void checkDuration(BigDecimal duration, String path) {
        if (duration == null) {
            return;
        }
        if (duration.signum() <= 0 || duration.compareTo(MAX_DURATION) > 0) {
            throw fail(path, "debe ser mayor que 0 y como máximo 1000000");
        }
        checkScale(duration, path);
    }

    private static void checkScale(BigDecimal value, String path) {
        if (value.stripTrailingZeros().scale() > MAX_SCALE) {
            throw fail(path, "admite como máximo " + MAX_SCALE + " decimales");
        }
    }

    private static BusinessException fail(String path, String reason) {
        return new BusinessException(path + ": " + reason);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
