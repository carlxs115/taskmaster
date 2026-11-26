package com.taskmaster.taskmasterbackend.dto.transfer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Proyecto dentro del fichero de exportación e importación de datos.
 *
 * @param name              nombre del proyecto
 * @param description       descripción, o {@code null}
 * @param status            estado; si falta al importar, {@code TODO}
 * @param priority          prioridad; si falta al importar, {@code MEDIUM}
 * @param category          categoría del proyecto
 * @param estimatedDuration duración estimada en horas, o {@code null}
 * @param createdAt         fecha de creación original (informativa; se ignora al importar)
 * @param tasks             tareas raíz del proyecto, con sus subtareas anidadas
 * @author Carlos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectData(
        String name,
        String description,
        TaskStatus status,
        TaskPriority priority,
        TaskCategory category,
        BigDecimal estimatedDuration,
        LocalDateTime createdAt,
        List<TaskData> tasks
) {
}
