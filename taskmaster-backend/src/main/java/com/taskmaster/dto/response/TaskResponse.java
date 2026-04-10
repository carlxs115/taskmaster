package com.taskmaster.dto.response;

import com.taskmaster.model.enums.TaskCategory;
import com.taskmaster.model.enums.TaskPriority;
import com.taskmaster.model.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO DE RESPUESTA DE TAREA
 *
 * Lo que el backend devuelve al frontend cuando consulta tareas.
 */
@Data
@Builder
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private Long projectId;
    private Long parentTaskId; // null si es tarea raíz
    private boolean deleted;
    private LocalDateTime deletedAt;
    private TaskCategory category;
}
