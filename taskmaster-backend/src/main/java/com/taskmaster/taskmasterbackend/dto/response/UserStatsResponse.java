package com.taskmaster.taskmasterbackend.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * DTO de respuesta con las estadísticas de actividad del usuario.
 *
 * <p>Los valores se calculan en tiempo real en la capa de servicio,
 * sin necesidad de una entidad de estadísticas en la base de datos.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class UserStatsResponse {

    /** Número total de tareas activas del usuario. */
    private long totalTasks;

    /** Número de tareas completadas. */
    private long completedTasks;

    /** Número de tareas pendientes (en estado TODO). */
    private long pendingTasks;

    /** Número de tareas en curso. */
    private long inProgressTasks;

    /** Número de tareas canceladas. */
    private long cancelledTasks;

    /** Número total de proyectos activos del usuario. */
    private long totalProjects;

    /** Porcentaje de tareas completadas sobre el total (0-100). */
    private int completionRate;
}
