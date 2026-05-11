package com.taskmaster.taskmasterfrontend.util;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

/**
 * Utilidad con métodos estáticos compartidos por los controladores de tareas
 * y proyectos para traducir estados, prioridades y aplicar estilos visuales.
 *
 * <p>Centraliza la lógica de colores, badges y aplicación de temas para
 * evitar duplicación entre {@code TaskDetailController} y
 * {@code ProjectDetailController}.</p>
 *
 * @author Carlos
 */
public final class TaskStyleHelper {

    /** Clase de utilidad, no instanciable. */
    private TaskStyleHelper() {}

    // -------------------------------------------------------------------------
    // Colores
    // -------------------------------------------------------------------------

    /**
     * Devuelve el color hex asociado a un estado de tarea o proyecto.
     *
     * @param status código de estado (p.ej. {@code "IN_PROGRESS"})
     * @return color en formato hex
     */
    public static String getStatusColor(String status) {
        return switch (status) {
            case "TODO" -> "#95a5a6";
            case "IN_PROGRESS" -> "#3498db";
            case "DONE" -> "#2ecc71";
            case "SUBMITTED" -> "#8e44ad";
            case "CANCELLED" -> "#e74c3c";
            default -> "#95a5a6";
        };
    }

    /**
     * Devuelve el color hex asociado a una prioridad de tarea o proyecto.
     *
     * @param priority código de prioridad (p.ej. {@code "HIGH"})
     * @return color en formato hex
     */
    public static String getPriorityColor(String priority) {
        return switch (priority) {
            case "URGENT" -> "#e74c3c";
            case "HIGH" -> "#e67e22";
            case "MEDIUM" -> "#3498db";
            case "LOW" -> "#95a5a6";
            default -> "#95a5a6";
        };
    }

    /**
     * Devuelve el estilo CSS inline del badge de categoría.
     *
     * @param category código de categoría (p.ej. {@code "PERSONAL"})
     * @return cadena de estilo CSS con color de fondo y de texto
     */
    public static String getCategoryBadgeStyle(String category) {
        return switch (category) {
            case "PERSONAL" -> "-fx-background-color: #f3e8ff; -fx-text-fill: #6b21a8;";
            case "ESTUDIOS" -> "-fx-background-color: #fef3c7; -fx-text-fill: #92400e;";
            case "TRABAJO" -> "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af;";
            default -> "-fx-background-color: #f0f0f5; -fx-text-fill: #666666;";
        };
    }

    // -------------------------------------------------------------------------
    // Traducciones
    // -------------------------------------------------------------------------

    /**
     * Traduce un código de estado del backend a su etiqueta localizada.
     *
     * @param status código de estado (p.ej. {@code "TODO"})
     * @param lm gestor de idioma activo
     * @return etiqueta localizada
     */
    public static String translateStatus(String status, LanguageManager lm) {
        return switch (status) {
            case "TODO" -> lm.get("status.TODO");
            case "IN_PROGRESS" -> lm.get("status.IN_PROGRESS");
            case "DONE" -> lm.get("status.DONE");
            case "SUBMITTED" -> lm.get("status.SUBMITTED");
            case "CANCELLED" -> lm.get("status.CANCELLED");
            default -> status;
        };
    }

    /**
     * Traduce un código de prioridad del backend a su etiqueta localizada.
     *
     * @param priority código de prioridad (p.ej. {@code "MEDIUM"})
     * @param lm gestor de idioma activo
     * @return etiqueta localizada
     */
    public static String translatePriority(String priority, LanguageManager lm) {
        return switch (priority) {
            case "LOW" -> lm.get("priority.LOW");
            case "MEDIUM" -> lm.get("priority.MEDIUM");
            case "HIGH" -> lm.get("priority.HIGH");
            case "URGENT" -> lm.get("priority.URGENT");
            default -> priority;
        };
    }

    // -------------------------------------------------------------------------
    // Tema
    // -------------------------------------------------------------------------

    /**
     * Aplica el tema activo del {@link ThemeManager} a la escena indicada,
     * cargando primero el CSS base y luego el CSS del tema activo si es distinto.
     * Usado por {@code TaskDetailController} y {@code ProjectDetailController}.
     *
     * @param scene escena a la que aplicar el tema
     * @param context  cualquier objeto con acceso a {@code getClass().getResource()}
     */
    public static void applyThemeToScene(Scene scene, Object context) {
        ThemeManager tm = ThemeManager.getInstance();

        var baseResource = context.getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css");
        if (baseResource != null) {scene.getStylesheets().add(baseResource.toExternalForm());}

        String cssPath = "/com/taskmaster/taskmasterfrontend/themes/" + tm.getCssFileNamePublic();
        var themeResource = context.getClass().getResource(cssPath);
        if (themeResource != null && (baseResource == null || !themeResource.toExternalForm().equals(
                baseResource.toExternalForm()))) {
            scene.getStylesheets().add(themeResource.toExternalForm());
        }

        scene.setFill(Color.web(tm.getBgApp()));
    }
}