package com.taskmaster.taskmasterbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.taskmaster.taskmasterbackend.dto.response.ImportResultResponse;
import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.dto.transfer.DependencyData;
import com.taskmaster.taskmasterbackend.dto.transfer.ProjectData;
import com.taskmaster.taskmasterbackend.dto.transfer.TaskData;
import com.taskmaster.taskmasterbackend.dto.transfer.WorkLogData;
import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.WorkLog;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exporta e importa los datos de una cuenta: proyectos, tareas, subtareas, registros de
 * trabajo y dependencias, en JSON (ida y vuelta) o CSV (solo exportación).
 *
 * <p><b>Aislamiento entre cuentas:</b> la exportación parte solo de consultas por
 * {@code userId} (nunca de ids que mande el cliente) y comprueba el dueño de las dos puntas
 * de cada dependencia. La importación no acepta ids, usuarios ni dueños del fichero.</p>
 *
 * <p>La importación lee y valida el fichero entero sin transacción y solo después delega
 * la escritura, atómica, en {@link DataImportWriter}.</p>
 *
 * @author Carlos
 */
@Slf4j
@Service
public class DataTransferService {

    /** Tamaño máximo del fichero de importación (32 MB), igual que el que aplica el cliente. */
    static final long MAX_IMPORT_BYTES = 32L * 1024 * 1024;

    private static final String TOO_LARGE_MESSAGE = "El archivo supera el tamaño máximo permitido";

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WorkLogRepository workLogRepository;
    private final DataImportWriter importWriter;
    private final DataImportValidator validator;
    private final TaskCsvWriter csvWriter = new TaskCsvWriter();
    private final ObjectWriter jsonWriter;
    private final long maxImportBytes;

    /**
     * Constructor de Spring.
     *
     * @param objectMapper el mapper de Spring Boot; no se modifica, el escritor y el lector
     *                     estricto se derivan de él
     */
    @Autowired
    public DataTransferService(ProjectRepository projectRepository, TaskRepository taskRepository,
                               WorkLogRepository workLogRepository, DataImportWriter importWriter,
                               ObjectMapper objectMapper) {
        this(projectRepository, taskRepository, workLogRepository, importWriter, objectMapper,
                new DataImportValidator(objectMapper), MAX_IMPORT_BYTES);
    }

    /** Constructor con validador y tamaño máximo propios, para los tests. */
    DataTransferService(ProjectRepository projectRepository, TaskRepository taskRepository,
                        WorkLogRepository workLogRepository, DataImportWriter importWriter,
                        ObjectMapper objectMapper, DataImportValidator validator, long maxImportBytes) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.workLogRepository = workLogRepository;
        this.importWriter = importWriter;
        this.validator = validator;
        this.maxImportBytes = maxImportBytes;
        this.jsonWriter = objectMapper.writer()
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .with(SerializationFeature.INDENT_OUTPUT);
    }

    // -------------------------------------------------------------------------
    // Exportación
    // -------------------------------------------------------------------------

    /**
     * Exporta los datos activos del usuario como JSON.
     *
     * @param userId identificador del usuario
     * @return bytes del JSON en UTF-8
     */
    @Transactional(readOnly = true)
    public byte[] exportJson(Long userId) {
        try {
            return jsonWriter.writeValueAsBytes(buildExport(userId));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("No se pudo generar la exportación", e);
        }
    }

    /**
     * Exporta las tareas activas del usuario como CSV.
     *
     * @param userId  identificador del usuario
     * @param spanish {@code true} para cabeceras en español, separador {@code ;} y coma decimal
     * @return bytes del CSV en UTF-8 con BOM
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long userId, boolean spanish) {
        return csvWriter.write(buildExport(userId), spanish);
    }

    /**
     * Construye en memoria la exportación del usuario: proyectos activos con sus tareas
     * (y subtareas anidadas), tareas personales, registros de trabajo y dependencias.
     * Nada que no sea alcanzable desde un proyecto activo o una tarea personal raíz activa
     * se exporta (la papelera y lo que cuelga de ella quedan fuera).
     */
    private DataExportFile buildExport(Long userId) {
        List<Project> projects = new ArrayList<>(projectRepository.findByUserIdAndDeletedFalse(userId));
        projects.sort(Comparator.comparing(Project::getId));
        Set<Long> activeProjectIds = new HashSet<>();
        for (Project project : projects) {
            activeProjectIds.add(project.getId());
        }

        List<Task> tasks = new ArrayList<>(taskRepository.findByUserIdAndDeletedFalse(userId));
        tasks.sort(Comparator.comparing(Task::getId));

        Map<Long, List<WorkLog>> workLogsByTask = new HashMap<>();
        List<WorkLog> workLogs = new ArrayList<>(workLogRepository.findByTaskUserId(userId));
        workLogs.sort(Comparator.comparing(WorkLog::getDate).thenComparing(WorkLog::getId));
        for (WorkLog workLog : workLogs) {
            workLogsByTask.computeIfAbsent(workLog.getTask().getId(), k -> new ArrayList<>()).add(workLog);
        }

        Map<Long, List<Task>> childrenByParent = new HashMap<>();
        Map<Long, List<Task>> rootsByProject = new HashMap<>();
        List<Task> personalRoots = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getParentTask() != null) {
                childrenByParent.computeIfAbsent(task.getParentTask().getId(), k -> new ArrayList<>()).add(task);
            } else if (task.getProject() == null) {
                personalRoots.add(task);
            } else if (activeProjectIds.contains(task.getProject().getId())) {
                rootsByProject.computeIfAbsent(task.getProject().getId(), k -> new ArrayList<>()).add(task);
            }
        }

        TreeBuilder builder = new TreeBuilder(childrenByParent, workLogsByTask);
        List<ProjectData> projectData = new ArrayList<>();
        for (Project project : projects) {
            List<TaskData> projectTasks = new ArrayList<>();
            for (Task root : rootsByProject.getOrDefault(project.getId(), List.of())) {
                projectTasks.add(builder.build(root));
            }
            projectData.add(new ProjectData(project.getName(), project.getDescription(), project.getStatus(),
                    project.getPriority(), project.getCategory(), project.getEstimatedDuration(),
                    project.getCreatedAt(), projectTasks));
        }
        List<TaskData> personalData = new ArrayList<>();
        for (Task root : personalRoots) {
            personalData.add(builder.build(root));
        }

        // Solo los pares cuyas dos tareas se exportan; la consulta ya filtra el dueño de las dos puntas
        List<int[]> pairs = new ArrayList<>();
        for (Object[] pair : taskRepository.findDependencyPairsByUserId(userId)) {
            Integer from = builder.indexById.get(((Number) pair[0]).longValue());
            Integer to = builder.indexById.get(((Number) pair[1]).longValue());
            if (from != null && to != null) {
                pairs.add(new int[]{from, to});
            }
        }
        pairs.sort(Comparator.<int[]>comparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
        List<DependencyData> dependencies = new ArrayList<>();
        for (int[] pair : pairs) {
            dependencies.add(new DependencyData(refOf(pair[0]), refOf(pair[1])));
        }

        return new DataExportFile(DataExportFile.FORMAT, DataExportFile.VERSION,
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), projectData, personalData, dependencies);
    }

    private static String refOf(int index) {
        return "t" + index;
    }

    /** Convierte tareas en {@link TaskData} asignando las referencias en orden de recorrido. */
    private static final class TreeBuilder {
        private final Map<Long, List<Task>> childrenByParent;
        private final Map<Long, List<WorkLog>> workLogsByTask;
        /** Índice de referencia de cada tarea exportada, por id de la base de datos. */
        final Map<Long, Integer> indexById = new HashMap<>();

        TreeBuilder(Map<Long, List<Task>> childrenByParent, Map<Long, List<WorkLog>> workLogsByTask) {
            this.childrenByParent = childrenByParent;
            this.workLogsByTask = workLogsByTask;
        }

        TaskData build(Task task) {
            // Un conjunto de visitados evita un bucle si hubiera una relación padre corrupta en BD
            indexById.put(task.getId(), indexById.size() + 1);
            String ref = refOf(indexById.get(task.getId()));

            List<WorkLogData> workLogs = new ArrayList<>();
            for (WorkLog workLog : workLogsByTask.getOrDefault(task.getId(), List.of())) {
                workLogs.add(new WorkLogData(workLog.getDate(), workLog.getActivityType(),
                        workLog.getHours(), workLog.getNote()));
            }
            List<TaskData> subtasks = new ArrayList<>();
            for (Task child : childrenByParent.getOrDefault(task.getId(), List.of())) {
                if (!indexById.containsKey(child.getId())) {
                    subtasks.add(build(child));
                }
            }
            return new TaskData(ref, task.getTitle(), task.getDescription(), task.getStatus(),
                    task.getPriority(), task.getCategory(), task.getDueDate(), task.getEstimatedDuration(),
                    task.getCreatedAt(), workLogs, subtasks);
        }
    }

    // -------------------------------------------------------------------------
    // Importación
    // -------------------------------------------------------------------------

    /**
     * Importa de forma aditiva un fichero JSON exportado por TaskMaster.
     *
     * <p>Sin {@code @Transactional}: la lectura y la validación no mantienen abierta una
     * transacción mientras llega el cuerpo; solo la escritura de {@link DataImportWriter}
     * es transaccional. Un fichero no válido no escribe nada.</p>
     *
     * @param userId identificador del usuario que importa
     * @param body   contenido del fichero
     * @return número de elementos creados
     * @throws BusinessException       si el fichero está vacío o no es válido
     * @throws ResponseStatusException 413 si supera el tamaño máximo
     */
    public ImportResultResponse importJson(Long userId, InputStream body) {
        byte[] bytes = readLimited(body);
        if (bytes.length == 0) {
            throw new BusinessException("El archivo está vacío");
        }
        DataExportFile file = validator.parse(bytes);
        validator.validate(file);

        ImportResultResponse result = importWriter.write(userId, file);
        log.info("Importación de datos para usuario id {}: {} proyectos, {} tareas, {} registros, {} dependencias",
                userId, result.projects(), result.tasks(), result.workLogs(), result.dependencies());
        return result;
    }

    /** Lee el cuerpo entero con tope de tamaño. */
    private byte[] readLimited(InputStream body) {
        try {
            byte[] bytes = body.readNBytes((int) maxImportBytes + 1);
            if (bytes.length > maxImportBytes) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE_MESSAGE);
            }
            return bytes;
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo");
        }
    }
}
