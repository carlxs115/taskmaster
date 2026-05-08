package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controlador de la pantalla de papelera.
 *
 * <p>Muestra las tareas y proyectos eliminados lógicamente, indicando los días
 * de retención configurados. Permite restaurar elementos a su estado activo
 * o eliminarlos permanentemente tras confirmación.</p>
 *
 * @author Carlos
 */
public class TrashController {

    private static final Logger log = LoggerFactory.getLogger(TrashController.class);

    @FXML private VBox trashTaskContainer;
    @FXML private VBox trashProjectContainer;
    @FXML private Label emptyTasksLabel;
    @FXML private Label emptyProjectsLabel;
    @FXML private Label retentionLabel;

    private Runnable onTrashChanged;
    private int retentionDays = 30;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final LanguageManager lm = LanguageManager.getInstance();

    /** Formato de fecha para mostrar fechas de eliminación y purga. */
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(
            "d MMM yyyy", LanguageManager.getInstance().getCurrentLocale());

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Registra el callback que se ejecutará al restaurar un elemento,
     * para que el controlador padre pueda refrescar su vista.
     *
     * @param callback acción a ejecutar al detectar un cambio en la papelera
     */
    public void setOnTrashChanged(Runnable callback) {
        this.onTrashChanged = callback;
    }

    /**
     * Inicializa la pantalla cargando las tareas, los proyectos eliminados
     * y los días de retención configurados.
     */
    @FXML
    public void initialize() {
        loadTrashTasks();
        loadTrashProjects();
        loadRetentionDays();
    }

    // -------------------------------------------------------------------------
    // Carga de datos
    // -------------------------------------------------------------------------

    /**
     * Obtiene los días de retención de la papelera desde los ajustes del backend
     * y actualiza la etiqueta informativa.
     */
    private void loadRetentionDays() {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/settings");

                if (response.statusCode() == 200) {
                    JsonNode settings = objectMapper.readTree(response.body());
                    int days = settings.get("trashRetentionDays").asInt();
                    Platform.runLater(() -> {
                        retentionDays = days;
                        retentionLabel.setText(MessageFormat.format(lm.get("trash.retention"), days));
                    });
                }
            } catch (Exception e) {
                log.error("Error al cargar los días de retención: {}", e.getMessage());
            }
        }, "trash-load-retention");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Obtiene las tareas eliminadas del backend y las renderiza en la vista.
     */
    private void loadTrashTasks() {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks/trash");
                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTrashTasks(tasks));
                }
            } catch (Exception e) {
                log.error("Error al cargar las tareas de la papelera: {}", e.getMessage());
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("trash.error.load.tasks")));
            }
        }, "trash-load-tasks");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Obtiene los proyectos eliminados del backend y los renderiza en la vista.
     */
    private void loadTrashProjects() {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/projects/trash");
                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTrashProjects(projects));
                }
            } catch (Exception e) {
                log.error("Error al cargar los proyectos de la papelera: {}", e.getMessage());
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("trash.error.load.projects")));
            }
        }, "trash-load-projects");
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Renderizado
    // -------------------------------------------------------------------------

    /**
     * Renderiza la lista de tareas eliminadas o muestra el mensaje de vacío.
     *
     * @param tasks array JSON con las tareas eliminadas
     */
    private void renderTrashTasks(JsonNode tasks) {
        trashTaskContainer.getChildren().clear();
        trashTaskContainer.getChildren().add(emptyTasksLabel);

        if (!tasks.isArray() || tasks.isEmpty()) {
            emptyTasksLabel.setVisible(true);
            return;
        }

        emptyTasksLabel.setVisible(false);
        for (JsonNode task : tasks) {
            trashTaskContainer.getChildren().add(createTrashTaskCard(task));
        }
    }

    /**
     * Renderiza la lista de proyectos eliminados o muestra el mensaje de vacío.
     *
     * @param projects array JSON con los proyectos eliminados
     */
    private void renderTrashProjects(JsonNode projects) {
        trashProjectContainer.getChildren().clear();
        trashProjectContainer.getChildren().add(emptyProjectsLabel);

        if (!projects.isArray() || projects.isEmpty()) {
            emptyProjectsLabel.setVisible(true);
            return;
        }

        emptyProjectsLabel.setVisible(false);
        for (JsonNode project : projects) {
            trashProjectContainer.getChildren().add(createTrashProjectCard(project));
        }
    }

    /**
     * Construye la tarjeta visual de una tarea eliminada con su título,
     * badges de prioridad y categoría, fechas de eliminación/purga y
     * botones de restaurar y eliminar permanentemente.
     *
     * @param task nodo JSON con los datos de la tarea
     * @return {@link HBox} con el contenido visual de la tarjeta
     */
    private HBox createTrashTaskCard(JsonNode task) {
        long taskId = task.get("id").asLong();
        String title = task.get("title").asText();
        String priority = task.get("priority").asText();
        String category = task.has("category") && !task.get("category").isNull()
                ? task.get("category").asText() : "PERSONAL";

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("task-title-done");

        Label priorityBadge = new Label(translatePriority(priority));
        priorityBadge.getStyleClass().add("detail-section-count");

        Label categoryBadge = new Label(translateCategory(category));
        categoryBadge.getStyleClass().add("detail-section-count");

        Button restoreBtn = buildRestoreButton(() -> restoreTask(taskId));
        Button deleteBtn  = buildDeleteButton(() -> permanentlyDeleteTask(taskId));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label datesLabel = buildDatesLabel(task);

        HBox card = new HBox(12, titleLabel, priorityBadge, categoryBadge,
                spacer, datesLabel, restoreBtn, deleteBtn);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("task-card");

        return card;
    }

    /**
     * Construye la tarjeta visual de un proyecto eliminado con su nombre,
     * badges de prioridad y categoría, fechas y botones de acción.
     *
     * @param project nodo JSON con los datos del proyecto
     * @return {@link HBox} con el contenido visual de la tarjeta
     */
    private HBox createTrashProjectCard(JsonNode project) {
        long   projectId = project.get("id").asLong();
        String name      = project.get("name").asText();
        String category  = project.get("category").asText();
        String priority  = project.has("priority") && !project.get("priority").isNull()
                ? project.get("priority").asText() : "MEDIUM";

        FontIcon folderIcon = new FontIcon("fas-folder");
        folderIcon.getStyleClass().add("trash-project-icon");

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("task-title-done");

        HBox titleBox = new HBox(8, folderIcon, nameLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label priorityBadge = new Label(translatePriority(priority));
        priorityBadge.getStyleClass().add("detail-section-count");

        Label categoryBadge = new Label(translateCategory(category));
        categoryBadge.getStyleClass().add("detail-section-count");

        Button restoreBtn = buildRestoreButton(() -> restoreProject(projectId));
        Button deleteBtn  = buildDeleteButton(() -> permanentlyDeleteProject(projectId));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label datesLabel = buildDatesLabel(project);

        HBox card = new HBox(12, titleBox, priorityBadge, categoryBadge,
                spacer, datesLabel, restoreBtn, deleteBtn);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("task-card");
        return card;
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    /**
     * Restaura una tarea eliminada y recarga la lista si la operación es exitosa.
     *
     * @param taskId identificador de la tarea a restaurar
     */
    private void restoreTask(Long taskId) {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().putNoBody("/api/tasks/" + taskId + "/restore");

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        loadTrashTasks();
                        if (onTrashChanged != null) onTrashChanged.run();
                    } else {
                        showAlert(lm.get("error.title"), lm.get("trash.error.restore.task"));
                    }
                });
            } catch (Exception e) {
                log.error("Error al restaurar tarea {}: {}", taskId, e.getMessage());
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("error.connection")));
            }
        }, "trash-restore-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Muestra confirmación y elimina permanentemente una tarea.
     *
     * @param taskId identificador de la tarea a eliminar permanentemente
     */
    private void permanentlyDeleteTask(long taskId) {
        if (userCancelledDelete()) return;
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().delete("/api/tasks/" + taskId + "/permanent");
                Platform.runLater(() -> {
                    if (r.statusCode() == 200 || r.statusCode() == 204) {
                        loadTrashTasks();
                    } else {
                        showAlert(lm.get("error.title"), lm.get("error.delete.task"));
                    }
                });
            } catch (Exception e) {
                log.error("Error al eliminar tarea {}: {}", taskId, e.getMessage());
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("settings.connection.error")));
            }
        }, "trash-delete-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Restaura un proyecto eliminado y recarga la lista si la operación es exitosa.
     *
     * @param projectId identificador del proyecto a restaurar
     */
    private void restoreProject(long projectId) {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().putNoBody("/api/projects/" + projectId + "/restore");
                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        loadTrashProjects();
                        if (onTrashChanged != null) onTrashChanged.run();
                    } else {
                        showAlert(lm.get("error.title"), lm.get("trash.error.restore.project"));
                    }
                });
            } catch (Exception e) {
                log.error("Error al restaurar proyecto {}: {}", projectId, e.getMessage());
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("error.connection")));
            }
        }, "trash-restore-project");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Muestra confirmación y elimina permanentemente un proyecto.
     *
     * @param projectId identificador del proyecto a eliminar permanentemente
     */
    private void permanentlyDeleteProject(long projectId) {
        if (userCancelledDelete()) return;
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().delete("/api/projects/" + projectId + "/permanent");
                Platform.runLater(() -> {
                    if (r.statusCode() == 200 || r.statusCode() == 204) {
                        loadTrashProjects();
                    } else {
                        showAlert(lm.get("error.title"), lm.get("error.delete.project"));
                    }
                });
            } catch (Exception e) {
                log.error("Error al eliminar proyecto {}: {}", projectId, e.getMessage());
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("settings.connection.error")));
            }
        }, "trash-delete-project");
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * Muestra confirmación y vacía toda la papelera (tareas y proyectos).
     */
    @FXML
    private void handleEmptyTrash() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("trash.empty.confirm.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("trash.empty.confirm.content"));
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Thread thread = new Thread(() -> {
                    try {
                        AppContext.getInstance().getApiService()
                                .delete("/api/tasks/trash/empty");
                        AppContext.getInstance().getApiService()
                                .delete("/api/projects/trash/empty");
                        Platform.runLater(() -> {
                            loadTrashTasks();
                            loadTrashProjects();
                            if (onTrashChanged != null) onTrashChanged.run();
                        });
                    } catch (Exception e) {
                        log.error("Error al vaciar la papelera: {}", e.getMessage());
                        Platform.runLater(() ->
                                showAlert(lm.get("error.title"), lm.get("error.connection")));
                    }
                }, "trash-empty");
                thread.setDaemon(true);
                thread.start();
            }
        });
    }

    /**
     * Recarga tanto las tareas como los proyectos eliminados desde el backend.
     * Puede llamarse desde el controlador padre para forzar una actualización.
     */
    public void refresh() {
        loadTrashTasks();
        loadTrashProjects();
    }

    // -------------------------------------------------------------------------
    // Métodos privados de construcción de UI
    // -------------------------------------------------------------------------

    /**
     * Construye el botón de restaurar con icono y estilo estándar.
     *
     * @param action acción a ejecutar al pulsar el botón
     * @return botón configurado
     */
    private Button buildRestoreButton(Runnable action) {
        Button btn = new Button(lm.get("trash.restore"));
        btn.getStyleClass().add("btn-small-primary");
        FontIcon icon = new FontIcon("fas-undo");
        icon.getStyleClass().add("btn-small-primary-icon");
        btn.setGraphic(icon);
        btn.setStyle("-fx-padding: 7 16 7 16;");
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(6);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    /**
     * Construye el botón de eliminar permanentemente con icono y estilo de peligro.
     *
     * @param action acción a ejecutar al pulsar el botón
     * @return botón configurado
     */
    private Button buildDeleteButton(Runnable action) {
        Button btn = new Button(lm.get("trash.delete"));
        btn.getStyleClass().add("btn-danger");
        FontIcon icon = new FontIcon("fas-times");
        icon.getStyleClass().add("btn-danger-icon");
        btn.setGraphic(icon);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(6);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    /**
     * Construye la etiqueta con las fechas de eliminación y purga prevista
     * de un elemento de la papelera.
     *
     * @param node nodo JSON con el campo {@code deletedAt}
     * @return etiqueta con las fechas formateadas, o etiqueta vacía si no hay fecha
     */
    private Label buildDatesLabel(JsonNode node) {
        Label label = new Label();
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #aaaaaa;");
        if (node.has("deletedAt") && !node.get("deletedAt").isNull()) {
            try {
                LocalDateTime deletedAt = LocalDateTime.parse(node.get("deletedAt").asText());
                LocalDate purgeDate     = deletedAt.toLocalDate().plusDays(retentionDays);
                label.setText(
                        lm.get("trash.deleted.on") + " "
                                + deletedAt.toLocalDate().format(dateFormatter)
                                + "  ·  "
                                + lm.get("trash.purge.on") + " "
                                + purgeDate.format(dateFormatter));
            } catch (Exception ignored) {}
        }
        return label;
    }

    // -------------------------------------------------------------------------
    // Métodos privados de utilidad
    // -------------------------------------------------------------------------

    /**
     * Muestra el diálogo de confirmación de eliminación permanente.
     *
     * @return {@code true} si el usuario confirma, {@code false} si cancela
     */
    private boolean userCancelledDelete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("trash.delete.confirm.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("trash.delete.confirm.content"));
        return confirm.showAndWait()
                .map(r -> r != ButtonType.OK)
                .orElse(true);
    }

    /**
     * Traduce un código de prioridad del backend a su etiqueta localizada.
     *
     * @param priority código de prioridad (p.ej. {@code "HIGH"})
     * @return etiqueta localizada
     */
    private String translatePriority(String priority) {
        return switch (priority) {
            case "LOW"    -> lm.get("priority.LOW");
            case "MEDIUM" -> lm.get("priority.MEDIUM");
            case "HIGH"   -> lm.get("priority.HIGH");
            case "URGENT" -> lm.get("priority.URGENT");
            default       -> priority;
        };
    }

    /**
     * Traduce un código de categoría del backend a su etiqueta localizada.
     *
     * @param category código de categoría (p.ej. {@code "PERSONAL"})
     * @return etiqueta localizada
     */
    private String translateCategory(String category) {
        return switch (category) {
            case "PERSONAL" -> lm.get("category.PERSONAL");
            case "ESTUDIOS" -> lm.get("category.ESTUDIOS");
            case "TRABAJO"  -> lm.get("category.TRABAJO");
            default         -> category;
        };
    }

    /**
     * Muestra un diálogo de información con el título y mensaje indicados.
     *
     * @param title   título del diálogo
     * @param message mensaje a mostrar
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}