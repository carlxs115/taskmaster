package com.taskmaster.taskmasterbackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO de respuesta que representa un proyecto junto con sus tareas.
 *
 * <p>Se usa dentro de {@link HomeResponse} para mostrar cada proyecto
 * y sus tareas en la pantalla de inicio.</p>
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

    /** Categoría del proyecto. */
    private String category;

    /** Lista de tareas asociadas al proyecto. */
    private List<TaskResponse> tasks;
}
