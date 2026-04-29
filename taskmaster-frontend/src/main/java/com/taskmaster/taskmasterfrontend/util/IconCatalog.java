package com.taskmaster.taskmasterfrontend.util;

/**
 * Catálogo centralizado de iconos de la aplicación.
 * Usa identificadores Ikonli (FontAwesome 5).
 * Si quieres cambiar un icono globalmente, modifícalo aquí.
 */
public class IconCatalog {

    private IconCatalog() {} // Clase de utilidad, no instanciable

    // ===== Sidebar =====
    public static final String SIDEBAR_HOME        = "fas-home";
    public static final String SIDEBAR_TASKS       = "fas-tasks";
    public static final String SIDEBAR_PROJECTS    = "fas-folder";
    public static final String SIDEBAR_PROJECTS_ON = "fas-folder-open";
    public static final String SIDEBAR_CALENDAR    = "fas-calendar-alt";
    public static final String SIDEBAR_STATS       = "fas-chart-bar";
    public static final String SIDEBAR_SETTINGS    = "fas-cog";

    // ===== Acciones CRUD =====
    public static final String ACTION_CREATE       = "fas-plus";
    public static final String ACTION_EDIT         = "fas-pen";
    public static final String ACTION_DELETE       = "fas-trash";
    public static final String ACTION_SAVE         = "fas-check";
    public static final String ACTION_CANCEL       = "fas-times";

    // ===== Prioridad de tareas =====
    public static final String PRIORITY_HIGH       = "fas-exclamation-circle";
    public static final String PRIORITY_MEDIUM     = "fas-minus-circle";
    public static final String PRIORITY_LOW        = "fas-arrow-down";

    // ===== Estado de tareas =====
    public static final String STATUS_PENDING      = "far-circle";
    public static final String STATUS_IN_PROGRESS  = "fas-spinner";
    public static final String STATUS_COMPLETED    = "fas-check-circle";
    public static final String STATUS_CANCELLED    = "fas-ban";

    // ===== Categorías =====
    public static final String CATEGORY_WORK       = "fas-briefcase";
    public static final String CATEGORY_PERSONAL   = "fas-user";
    public static final String CATEGORY_STUDIES    = "fas-graduation-cap";
    public static final String CATEGORY_HOME       = "fas-home";
    public static final String CATEGORY_HEALTH     = "fas-heartbeat";
    public static final String CATEGORY_SHOPPING   = "fas-shopping-cart";
    public static final String CATEGORY_OTHER      = "fas-tag";

    // ===== Usuario / Perfil =====
    public static final String USER_AVATAR         = "fas-user-circle";
    public static final String USER_PROFILE        = "fas-id-card";
    public static final String USER_PASSWORD       = "fas-key";
    public static final String USER_LOGIN_HISTORY  = "fas-history";
    public static final String USER_DELETE_ACCOUNT = "fas-user-times";
    public static final String USER_LOGOUT         = "fas-sign-out-alt";

    // ===== UI general =====
    public static final String UI_SEARCH           = "fas-search";
    public static final String UI_FILTER           = "fas-filter";
    public static final String UI_SORT             = "fas-sort";
    public static final String UI_NOTIFICATION     = "fas-bell";
    public static final String UI_BIRTHDAY         = "fas-birthday-cake";
    public static final String UI_OVERDUE          = "fas-exclamation-triangle";
    public static final String UI_SUBTASK          = "fas-level-down-alt";
    public static final String UI_BACK             = "fas-arrow-left";
    public static final String UI_MORE             = "fas-ellipsis-v";
    public static final String UI_INFO             = "fas-info-circle";
}
