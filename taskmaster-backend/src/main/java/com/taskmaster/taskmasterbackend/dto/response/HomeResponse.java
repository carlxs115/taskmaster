package com.taskmaster.taskmasterbackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO de respuesta que agrupa los datos necesarios para renderizar la pantalla de inicio.
 *
 * <p>Contiene los proyectos del usuario con sus tareas, y las tareas sin proyecto
 * agrupadas por categoría.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class HomeResponse {

    /** Lista de proyectos del usuario con sus tareas asociadas. */
    private List<ProjectWithTasksResponse> projects;

    /** Tareas personales sin proyecto ni categoría específica. */
    private List<TaskResponse> personalTasks;

    /** Tareas personales de la categoría ESTUDIOS. */
    private List<TaskResponse> estudiosTasks;

    /** Tareas personales de la categoría TRABAJO. */
    private List<TaskResponse> trabajoTasks;
}
