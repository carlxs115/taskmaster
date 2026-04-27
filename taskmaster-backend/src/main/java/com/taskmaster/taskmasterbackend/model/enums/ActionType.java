package com.taskmaster.taskmasterbackend.model.enums;

/**
 * Enumeración de los tipos de acción registrables en el historial de actividad.
 *
 * <p>Cubre todas las operaciones relevantes sobre tareas, subtareas,
 * proyectos, perfil de usuario y autenticación.</p>
 *
 * @author Carlos
 */
public enum ActionType {
    // ── Tareas ────────────────────────────────────────────────────────────────
    /** Tarea creada. */
    TASK_CREATED,
    /** Tarea editada. */
    TASK_EDITED,
    /** Tarea enviada a la papelera. */
    TASK_DELETED,
    /** Tarea eliminada definitivamente. */
    TASK_PERMANENTLY_DELETED,
    /** Tarea restaurada desde la papelera. */
    TASK_RESTORED,
    /** Estado de una tarea modificado. */
    TASK_STATUS_CHANGED,

    // ── Subtareas ─────────────────────────────────────────────────────────────
    /** Subtarea creada. */
    SUBTASK_CREATED,
    /** Subtarea editada. */
    SUBTASK_EDITED,
    /** Subtarea eliminada. */
    SUBTASK_DELETED,

    // ── Proyectos ─────────────────────────────────────────────────────────────
    /** Proyecto creado. */
    PROJECT_CREATED,
    /** Proyecto editado. */
    PROJECT_EDITED,
    /** Proyecto enviado a la papelera. */
    PROJECT_DELETED,
    /** Proyecto eliminado definitivamente. */
    PROJECT_PERMANENTLY_DELETED,
    /** Proyecto restaurado desde la papelera. */
    PROJECT_RESTORED,
    /** Estado de un proyecto modificado. */
    PROJECT_STATUS_CHANGED,

    // ── Perfil ────────────────────────────────────────────────────────────────
    /** Datos del perfil de usuario actualizados. */
    PROFILE_UPDATED,
    /** Contraseña del usuario modificada. */
    PASSWORD_CHANGED,

    // ── Autenticación ─────────────────────────────────────────────────────────
    /** Inicio de sesión. */
    LOGIN,
    /** Cierre de sesión. */
    LOGOUT
}
