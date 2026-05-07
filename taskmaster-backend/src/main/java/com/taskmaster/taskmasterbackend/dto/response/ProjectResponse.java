package com.taskmaster.taskmasterbackend.dto.response;

import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;

import java.time.LocalDateTime;

/**
 * DTO de respuesta con los datos de un proyecto.
 *
 * <p>Evita exponer la entidad {@link com.taskmaster.taskmasterbackend.model.Project}
 * directamente en la API, ocultando la referencia interna al usuario
 * y la lista de tareas.</p>
 *
 * @param id          identificador único del proyecto
 * @param name        nombre del proyecto
 * @param description descripción opcional del proyecto
 * @param status      estado actual del proyecto
 * @param priority    prioridad del proyecto
 * @param category    categoría del proyecto
 * @param createdAt   fecha y hora de creación
 * @param deleted     indica si el proyecto está en la papelera
 * @param deletedAt   fecha y hora en que fue enviado a la papelera, o {@code null} si no lo está
 *
 * @author Carlos
 */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        TaskStatus status,
        TaskPriority priority,
        TaskCategory category,
        LocalDateTime createdAt,
        boolean deleted,
        LocalDateTime deletedAt
) {}
