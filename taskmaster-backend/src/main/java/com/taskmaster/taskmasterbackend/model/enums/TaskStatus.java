package com.taskmaster.taskmasterbackend.model.enums;

/**
 * Enumeración de los posibles estados de una tarea o proyecto.
 *
 * @author Carlos
 */
public enum TaskStatus {
    /** Pendiente de iniciar. */
    TODO,
    /** En curso. */
    IN_PROGRESS,
    /** Completada. */
    DONE,
    /** Entregada. */
    SUBMITTED,
    /** Cancelada. */
    CANCELLED
}
