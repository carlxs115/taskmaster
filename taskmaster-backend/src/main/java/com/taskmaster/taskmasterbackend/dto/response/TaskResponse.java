package com.taskmaster.taskmasterbackend.dto.response;

import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta que representa una tarea.
 *
 * <p>Contiene todos los campos de una tarea que el backend devuelve al frontend,
 * incluyendo información de papelera y relaciones con proyecto y tarea padre.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class TaskResponse {

    /** Identificador único de la tarea. */
    private Long id;

    /** Título de la tarea. */
    private String title;

    /** Descripción de la tarea. */
    private String description;

    /** Estado actual de la tarea. */
    private TaskStatus status;

    /** Prioridad de la tarea. */
    private TaskPriority priority;

    /** Fecha límite de la tarea. */
    private LocalDate dueDate;

    /** Fecha y hora de creación de la tarea. */
    private LocalDateTime createdAt;

    /** Identificador del proyecto al que pertenece. {@code null} si es tarea sin proyecto. */
    private Long projectId;

    /** Identificador de la tarea padre. {@code null} si es tarea raíz. */
    private Long parentTaskId;

    /** Indica si la tarea está en la papelera. */
    private boolean deleted;

    /** Fecha y hora en que la tarea fue enviada a la papelera. */
    private LocalDateTime deletedAt;

    /** Categoría de la tarea. */
    private TaskCategory category;
}
