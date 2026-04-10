package com.taskmaster.model.enums;

public enum ActionType {
    // Tareas
    TASK_CREATED,
    TASK_EDITED,
    TASK_DELETED,
    TASK_PERMANENTLY_DELETED,
    TASK_RESTORED,
    TASK_STATUS_CHANGED,

    // Subtareas
    SUBTASK_CREATED,
    SUBTASK_EDITED,
    SUBTASK_DELETED,

    // Proyectos
    PROJECT_CREATED,
    PROJECT_EDITED,
    PROJECT_DELETED,
    PROJECT_PERMANENTLY_DELETED,
    PROJECT_RESTORED,
    PROJECT_STATUS_CHANGED,

    // Perfil
    PROFILE_UPDATED,
    PASSWORD_CHANGED,

    // Autenticación
    LOGIN,
    LOGOUT
}
