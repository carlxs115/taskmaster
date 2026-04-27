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
 * <p>Se reutiliza tanto en la creación como en la edición de tareas.
 * Si {@code parentTaskId} tiene valor, la tarea se crea como subtarea
 * de la tarea indicada.</p>
 *
 * @author Carlos
 */
@Data
public class TaskRequest {

    /** Título de la tarea. */
    @NotBlank(message = "El título es boligatorio")
    private String title;

    /** Descripción opcional de la tarea. */
    private String description;

    /** Prioridad de la tarea. */
    private TaskPriority priority;

    /** Fecha límite de la tarea. */
    private LocalDate dueDate;

    /** Identificador del proyecto al que pertenece la tarea. {@code null} si es tarea personal. */
    private Long projectId;

    /** Identificador de la tarea padre. {@code null} si es tarea raíz. */
    private Long parentTaskId;

    /** Categoría de la tarea. */
    private TaskCategory category;

    /** Estado de la tarea. */
    private TaskStatus status;
}
