package com.taskmaster.taskmasterbackend.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * DTO de respuesta con las estadísticas de actividad del usuario.
 *
 * <p>Los valores se calculan en tiempo real en {@code UserService.getStats},
 * consultando directamente los contadores en base de datos sin necesidad
 * de una entidad de estadísticas persistida.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class UserStatsResponse {

    /** Número total de tareas activas del usuario (no eliminadas). */
    private long totalTasks;

    /** Número de tareas en estado {@code DONE}. */
    private long completedTasks;

    /** Número de tareas en estado {@code TODO} (pendientes de iniciar). */
    private long pendingTasks;

    /** Número de tareas en estado {@code IN_PROGRESS}. */
    private long inProgressTasks;

    /** Número de tareas en estado {@code CANCELLED}. */
    private long cancelledTasks;

    /** Número total de proyectos activos del usuario (no eliminados). */
    private long totalProjects;

    /**
     * Porcentaje de tareas completadas sobre el total (0-100).
     * Devuelve {@code 0} cuando {@code totalTasks} es 0 para evitar
     * división por cero.
     */
    private int completionRate;
}