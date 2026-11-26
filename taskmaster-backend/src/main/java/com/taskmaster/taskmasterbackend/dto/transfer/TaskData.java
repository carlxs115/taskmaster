package com.taskmaster.taskmasterbackend.dto.transfer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tarea o subtarea dentro del fichero de exportación e importación de datos.
 *
 * @param ref               referencia local del fichero ({@code t1}, {@code t2}…), solo para
 *                          las dependencias; no es el id de la base de datos
 * @param title             título de la tarea
 * @param description       descripción, o {@code null}
 * @param status            estado; si falta al importar, {@code TODO}
 * @param priority          prioridad; si falta al importar, {@code MEDIUM}
 * @param category          categoría; en una tarea de proyecto se ignora y se hereda la del proyecto
 * @param dueDate           fecha límite, o {@code null}
 * @param estimatedDuration duración estimada en horas, o {@code null}
 * @param createdAt         fecha de creación original (informativa; se ignora al importar)
 * @param workLogs          registros de trabajo de la tarea
 * @param subtasks          subtareas, anidadas a cualquier profundidad
 * @author Carlos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskData(
        String ref,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        TaskCategory category,
        LocalDate dueDate,
        BigDecimal estimatedDuration,
        LocalDateTime createdAt,
        List<WorkLogData> workLogs,
        List<TaskData> subtasks
) {
}
