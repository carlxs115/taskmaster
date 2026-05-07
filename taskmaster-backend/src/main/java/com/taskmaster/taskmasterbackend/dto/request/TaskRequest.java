package com.taskmaster.taskmasterbackend.dto.request;

import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO para la creación y actualización de tareas.
 *
 * <p>Se reutiliza tanto en la creación ({@code POST /api/tasks}) como en la
 * edición ({@code PUT /api/tasks/{id}}) de tareas. Los campos opcionales
 * como {@code projectId}, {@code parentTaskId} y {@code dueDate} pueden
 * ser {@code null}.</p>
 *
 * <p>Si {@code parentTaskId} tiene valor, la tarea se crea como subtarea
 * de la tarea indicada y hereda su categoría.</p>
 *
 * <p>Si {@code projectId} tiene valor, la tarea pertenece a ese proyecto
 * y hereda la categoría del proyecto.</p>
 *
 * @author Carlos
 */
@Data
public class TaskRequest {

    /** Título de la tarea. No puede estar vacío. */
    @NotBlank(message = "El título es boligatorio")
    private String title;

    /** Descripción opcional de la tarea. */
    private String description;

    /**
     * Prioridad de la tarea.
     * Si es {@code null}, el servicio aplica {@code MEDIUM} por defecto.
     */
    private TaskPriority priority;

    /** Fecha límite opcional de la tarea. */
    private LocalDate dueDate;

    /**
     * Identificador del proyecto al que pertenece la tarea.
     * {@code null} si es una tarea personal sin proyecto.
     */
    private Long projectId;

    /**
     * Identificador de la tarea padre.
     * {@code null} si es una tarea raíz (no es subtarea de ninguna otra).
     */
    private Long parentTaskId;

    /**
     * Categoría de la tarea (PERSONAL, ESTUDIOS, TRABAJO).
     * Se ignora si la tarea pertenece a un proyecto,
     * en cuyo caso hereda la categoría del proyecto.
     */
    private TaskCategory category;

    /**
     * Estado de la tarea.
     * Si es {@code null} en creación, el servicio aplica {@code TODO} por defecto.
     */
    private TaskStatus status;
}