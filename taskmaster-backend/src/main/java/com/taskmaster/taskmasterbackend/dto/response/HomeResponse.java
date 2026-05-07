package com.taskmaster.taskmasterbackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO de respuesta que agrupa los datos necesarios para renderizar la pantalla de inicio.
 *
 * <p>Contiene los proyectos del usuario con sus tareas raíz activas, y las tareas
 * personales (sin proyecto) agrupadas por categoría.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class HomeResponse {

    /** Lista de proyectos activos del usuario con sus tareas raíz asociadas. */
    private List<ProjectWithTasksResponse> projects;

    /**
     * Tareas personales de categoría {@code PERSONAL}.
     * Son tareas sin proyecto asociado de la categoría más general.
     */
    private List<TaskResponse> personalTasks;

    /**
     * Tareas personales de categoría {@code ESTUDIOS}.
     * Son tareas sin proyecto asociado relacionadas con formación o estudios.
     */
    private List<TaskResponse> estudiosTasks;

    /**
     * Tareas personales de categoría {@code TRABAJO}.
     * Son tareas sin proyecto asociado relacionadas con el trabajo.
     */
    private List<TaskResponse> trabajoTasks;
}
