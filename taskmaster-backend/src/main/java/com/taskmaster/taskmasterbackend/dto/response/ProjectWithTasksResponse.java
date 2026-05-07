package com.taskmaster.taskmasterbackend.dto.response;

import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO de respuesta que representa un proyecto junto con sus tareas raíz activas.
 *
 * <p>Se usa dentro de {@link HomeResponse} para mostrar cada proyecto
 * y sus tareas en la pantalla de inicio. Solo incluye las tareas raíz,
 * no las subtareas.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class ProjectWithTasksResponse {

    /** Identificador único del proyecto. */
    private Long id;

    /** Nombre del proyecto. */
    private String name;

    /** Descripción opcional del proyecto. */
    private String description;

    /**
     * Estado actual del proyecto.
     * Tipado como enum en lugar de String para consistencia con {@link TaskResponse}
     * y para evitar errores por diferencias de mayúsculas en el frontend.
     */
    private TaskStatus status;

    /** Prioridad del proyecto. */
    private TaskPriority priority;

    /** Categoría del proyecto. */
    private TaskCategory category;

    /**
     * Lista de tareas raíz activas asociadas al proyecto.
     * No incluye subtareas ni tareas eliminadas.
     */
    private List<TaskResponse> tasks;
}