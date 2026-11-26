package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.dto.transfer.DependencyData;
import com.taskmaster.taskmasterbackend.dto.transfer.ProjectData;
import com.taskmaster.taskmasterbackend.dto.transfer.TaskData;
import com.taskmaster.taskmasterbackend.dto.transfer.WorkLogData;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escribe las tareas de una exportación como CSV, una fila por tarea o subtarea.
 *
 * <p>Es una clase pura (sin Spring ni base de datos). El CSV es solo de exportación: pensado
 * para abrirlo en una hoja de cálculo. El separador y el signo decimal dependen del idioma de
 * la interfaz (en español {@code ;} y coma, como espera Excel con configuración regional
 * española; en inglés {@code ,} y punto). El fichero va en UTF-8 con BOM y con fin de línea
 * CRLF.</p>
 *
 * <p><b>Seguridad:</b> los campos de texto del usuario que empiezan por {@code =}, {@code +},
 * {@code -}, {@code @}, tabulador o retorno de carro llevan delante un apóstrofo para que la
 * hoja de cálculo no los evalúe como fórmula (OWASP, «CSV Injection»).</p>
 *
 * @author Carlos
 */
public class TaskCsvWriter {

    /** Marca de orden de bytes UTF-8, para que Excel reconozca las tildes. */
    private static final String BOM = "﻿";

    /** Fin de línea de RFC 4180. */
    private static final String EOL = "\r\n";

    /** Prefijos que una hoja de cálculo interpreta como inicio de fórmula. */
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final List<String> HEADERS_ES = List.of(
            "Referencia", "Tarea padre", "Proyecto", "Título", "Descripción", "Estado",
            "Prioridad", "Categoría", "Fecha límite", "Duración estimada (h)",
            "Horas registradas", "Depende de", "Creada");

    private static final List<String> HEADERS_EN = List.of(
            "Ref", "Parent", "Project", "Title", "Description", "Status", "Priority", "Category",
            "Due date", "Estimated hours", "Logged hours", "Depends on", "Created");

    /**
     * Genera el CSV de las tareas de una exportación.
     *
     * @param file    exportación de la que se toman los proyectos, tareas y dependencias
     * @param spanish {@code true} para cabeceras en español, {@code ;} y coma decimal;
     *                {@code false} para inglés, {@code ,} y punto decimal
     * @return bytes del CSV en UTF-8 con BOM
     */
    public byte[] write(DataExportFile file, boolean spanish) {
        char separator = spanish ? ';' : ',';
        Map<String, List<String>> dependsOn = groupDependencies(file.dependencies());

        StringBuilder out = new StringBuilder(BOM);
        appendRow(out, separator, spanish ? HEADERS_ES : HEADERS_EN);

        for (ProjectData project : orEmpty(file.projects())) {
            for (TaskData task : orEmpty(project.tasks())) {
                appendTask(out, separator, spanish, project.name(), null, task, dependsOn);
            }
        }
        for (TaskData task : orEmpty(file.tasks())) {
            appendTask(out, separator, spanish, null, null, task, dependsOn);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Escribe la fila de una tarea y, a continuación, la de sus subtareas (preorden). */
    private void appendTask(StringBuilder out, char separator, boolean spanish, String project,
                            String parentRef, TaskData task, Map<String, List<String>> dependsOn) {
        BigDecimal logged = BigDecimal.ZERO;
        for (WorkLogData log : orEmpty(task.workLogs())) {
            if (log.hours() != null) {
                logged = logged.add(log.hours());
            }
        }
        List<String> deps = task.ref() == null ? List.of() : dependsOn.getOrDefault(task.ref(), List.of());

        List<String> fields = new ArrayList<>();
        fields.add(text(task.ref()));
        fields.add(text(parentRef));
        fields.add(userText(project));
        fields.add(userText(task.title()));
        fields.add(userText(task.description()));
        fields.add(text(task.status()));
        fields.add(text(task.priority()));
        fields.add(text(task.category()));
        fields.add(date(task.dueDate()));
        fields.add(number(task.estimatedDuration(), spanish));
        fields.add(number(logged, spanish));
        fields.add(String.join(" ", deps));
        fields.add(dateTime(task.createdAt()));
        appendRow(out, separator, fields);

        for (TaskData subtask : orEmpty(task.subtasks())) {
            appendTask(out, separator, spanish, project, task.ref(), subtask, dependsOn);
        }
    }

    /** Agrupa las dependencias por la referencia de la tarea que depende. */
    private static Map<String, List<String>> groupDependencies(List<DependencyData> dependencies) {
        Map<String, List<String>> grouped = new HashMap<>();
        for (DependencyData dependency : orEmpty(dependencies)) {
            grouped.computeIfAbsent(dependency.task(), k -> new ArrayList<>()).add(dependency.dependsOn());
        }
        return grouped;
    }

    /** Escribe una fila escapando cada campo y la termina con CRLF. */
    private static void appendRow(StringBuilder out, char separator, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(separator);
            }
            out.append(escape(fields.get(i), separator));
        }
        out.append(EOL);
    }

    /**
     * Escapa un campo según RFC 4180: va entre comillas si contiene el separador, comillas,
     * CR o LF, o empieza o acaba en espacio; las comillas se duplican.
     */
    static String escape(String value, char separator) {
        boolean quote = value.indexOf(separator) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || (!value.isEmpty() && (value.charAt(0) == ' ' || value.charAt(value.length() - 1) == ' '));
        if (!quote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Texto escrito por el usuario: se neutraliza el inicio de fórmula antes del escapado. */
    static String userText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private static String number(BigDecimal value, boolean spanish) {
        if (value == null) {
            return "";
        }
        String plain = value.toPlainString();
        return spanish ? plain.replace('.', ',') : plain;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
