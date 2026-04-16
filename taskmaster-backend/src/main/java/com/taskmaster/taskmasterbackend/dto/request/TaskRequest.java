package com.taskmaster.taskmasterbackend.dto.request;

import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO DE TAREA
 *
 * Se usa tanto para crear como para actualizar tareas.
 * parentTaskId es opcional - si viene, la tarea se crea como subtarea.
 */
@Data
public class TaskRequest {

    @NotBlank(message = "El título es boligatorio")
    private String title;

    private String description;
    private TaskPriority priority;
    private LocalDate dueDate;
    private Long projectId;
    private Long parentTaskId; // null si es tarea raíz
    private TaskCategory category;
    private TaskStatus status;
}
