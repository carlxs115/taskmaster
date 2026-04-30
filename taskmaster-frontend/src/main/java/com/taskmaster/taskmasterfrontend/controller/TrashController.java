package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.http.HttpResponse;

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

    @FXML private VBox trashTaskContainer;
    @FXML private VBox trashProjectContainer;
    @FXML private Label emptyTasksLabel;
    @FXML private Label emptyProjectsLabel;
    @FXML private Label retentionLabel;

    private Runnable onTrashChanged;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Registra el callback que se ejecutará al restaurar un elemento,
     * para que el controlador padre pueda refrescar su vista.
     *
     * @param callback Acción a ejecutar al detectar un cambio en la papelera.
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

    /**
     * Obtiene los días de retención de la papelera desde los ajustes del backend
     * y actualiza la etiqueta informativa.
     */
    private void loadRetentionDays() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/settings");

                if (response.statusCode() == 200) {
                    JsonNode settings = objectMapper.readTree(response.body());
                    int days = settings.get("trashRetentionDays").asInt();
                    Platform.runLater(() ->
                            retentionLabel.setText(java.text.MessageFormat.format(lm.get("trash.retention"), days)));
                }
            } catch (Exception e) {

            }
        }).start();
    }

    /**
     * Obtiene las tareas eliminadas del backend y las renderiza en la vista.
     */
    private void loadTrashTasks() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/tasks/trash");

                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTrashTasks(tasks));
                }

            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("trash.error.load.tasks")));
            }
        }).start();
    }

    /**
     * Obtiene los proyectos eliminados del backend y los renderiza en la vista.
     */
    private void loadTrashProjects() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/projects/trash");

                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTrashProjects(projects));
                }

            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert(lm.get("error.title"), lm.get("trash.error.load.projects")));
            }
        }).start();
    }

    /**
     * Renderiza la lista de tareas eliminadas o muestra el mensaje de vacío
     * si no hay ninguna.
     *
     * @param tasks Array JSON con las tareas eliminadas.
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
     * Renderiza la lista de proyectos eliminados o muestra el mensaje de vacío
     * si no hay ninguno.
     *
     * @param projects Array JSON con los proyectos eliminados.
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
     * badge de prioridad y botones de restaurar y eliminar permanentemente.
     *
     * @param task Nodo JSON con los datos de la tarea.
     * @return {@link HBox} con el contenido visual de la tarjeta.
     */
    private HBox createTrashTaskCard(JsonNode task) {
        HBox card = new HBox();
        card.setSpacing(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("task-card");

        Long taskId = task.get("id").asLong();
        String title = task.get("title").asText();
        String priority = task.get("priority").asText();

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("task-title-done");

        Label priorityBadge = new Label(translatePriority(priority));
        priorityBadge.getStyleClass().add("detail-section-count");

        Button restoreBtn = new Button(lm.get("trash.restore"));
        restoreBtn.getStyleClass().add("btn-small-primary");
        FontIcon restoreIcon = new FontIcon("fas-undo");
        restoreIcon.getStyleClass().add("btn-small-primary-icon");
        restoreBtn.setStyle("-fx-padding: 7 16 7 16;");
        restoreBtn.setGraphic(restoreIcon);
        restoreBtn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        restoreBtn.setGraphicTextGap(6);
        restoreBtn.setOnAction(e -> restoreTask(taskId));

        Button deleteBtn = new Button(lm.get("trash.delete"));
        deleteBtn.getStyleClass().add("btn-danger");
        FontIcon deleteIcon = new FontIcon("fas-times");
        deleteIcon.getStyleClass().add("btn-danger-icon");
        deleteBtn.setGraphic(deleteIcon);
        deleteBtn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        deleteBtn.setGraphicTextGap(6);
        deleteBtn.setOnAction(e -> permanentlyDeleteTask(taskId));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        card.getChildren().addAll(titleLabel, priorityBadge, spacer, restoreBtn, deleteBtn);
        return card;
    }

    /**
     * Construye la tarjeta visual de un proyecto eliminado con su nombre,
     * badge de categoría y botones de restaurar y eliminar permanentemente.
     *
     * @param project Nodo JSON con los datos del proyecto.
     * @return {@link HBox} con el contenido visual de la tarjeta.
     */
    private HBox createTrashProjectCard(JsonNode project) {
        HBox card = new HBox();
        card.setSpacing(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("task-card");

        Long projectId = project.get("id").asLong();
        String name = project.get("name").asText();
        String category = project.get("category").asText();

        FontIcon folderIcon = new FontIcon("fas-folder");
        folderIcon.getStyleClass().add("trash-project-icon");

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("task-title-done");

        HBox titleBox = new HBox(8, folderIcon, nameLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label categoryBadge = new Label(translateCategory(category));
        categoryBadge.getStyleClass().add("detail-section-count");

        Button restoreBtn = new Button(lm.get("trash.restore"));
        restoreBtn.getStyleClass().add("btn-small-primary");
        FontIcon restoreIcon = new FontIcon("fas-undo");
        restoreIcon.getStyleClass().add("btn-small-primary-icon");
        restoreBtn.setStyle("-fx-padding: 7 16 7 16;");
        restoreBtn.setGraphic(restoreIcon);
        restoreBtn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        restoreBtn.setGraphicTextGap(6);
        restoreBtn.setOnAction(e -> restoreProject(projectId));

        Button deleteBtn = new Button(lm.get("trash.delete"));
        deleteBtn.getStyleClass().add("btn-danger");
        FontIcon deleteIcon = new FontIcon("fas-times");
        deleteIcon.getStyleClass().add("btn-danger-icon");
        deleteBtn.setGraphic(deleteIcon);
        deleteBtn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        deleteBtn.setGraphicTextGap(6);
        deleteBtn.setOnAction(e -> permanentlyDeleteProject(projectId));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(titleBox, Priority.NEVER);
        card.getChildren().addAll(titleBox, categoryBadge, spacer, restoreBtn, deleteBtn);
        return card;
    }

    /**
     * Restaura una tarea eliminada enviando la solicitud al backend
     * y recarga la lista si la operación es exitosa.
     *
     * @param taskId Identificador de la tarea a restaurar.
     */
    private void restoreTask(Long taskId) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .putNoBody("/api/tasks/" + taskId + "/restore");

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        loadTrashTasks();
                        if (onTrashChanged != null) onTrashChanged.run();
                    } else {
                        showAlert(lm.get("error.title"), lm.get("trash.error.restore.task"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.connection")));
            }
        }).start();
    }

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * permanentemente una tarea del backend y recarga la lista.
     *
     * @param taskId Identificador de la tarea a eliminar permanentemente.
     */
    private void permanentlyDeleteTask(Long taskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("trash.delete.confirm.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("trash.delete.confirm.content"));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> httpResponse = AppContext.getInstance()
                                .getApiService()
                                .delete("/api/tasks/" + taskId + "/permanent");

                        Platform.runLater(() -> {
                            if (httpResponse.statusCode() == 200 || httpResponse.statusCode() == 204) {
                                loadTrashTasks();
                            } else {
                                showAlert(lm.get("error.title"), lm.get("error.delete.task"));
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("settings.connection.error")));
                    }
                }).start();
            }
        });
    }

    /**
     * Restaura un proyecto eliminado enviando la solicitud al backend
     * y recarga la lista si la operación es exitosa.
     *
     * @param projectId Identificador del proyecto a restaurar.
     */
    private void restoreProject(Long projectId) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .putNoBody("/api/projects/" + projectId + "/restore");

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        loadTrashProjects();
                        if (onTrashChanged != null) onTrashChanged.run();
                    } else {
                        showAlert(lm.get("error.title"), lm.get("trash.error.restore.project"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.connection")));
            }
        }).start();
    }

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * permanentemente un proyecto del backend y recarga la lista.
     *
     * @param projectId Identificador del proyecto a eliminar permanentemente.
     */
    private void permanentlyDeleteProject(Long projectId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("trash.delete.confirm.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("trash.delete.confirm.content"));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> httpResponse = AppContext.getInstance()
                                .getApiService()
                                .delete("/api/projects/" + projectId + "/permanent");

                        Platform.runLater(() -> {
                            if (httpResponse.statusCode() == 200 || httpResponse.statusCode() == 204) {
                                loadTrashProjects();
                            } else {
                                showAlert(lm.get("error.title"), lm.get("error.delete.project"));
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("settings.connection.error")));
                    }
                }).start();
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

    /**
     * Traduce un código de prioridad del backend a su etiqueta localizada.
     *
     * @param priority Código de prioridad (p.ej. {@code "HIGH"}).
     * @return Etiqueta localizada correspondiente.
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
     * @param category Código de categoría (p.ej. {@code "PERSONAL"}).
     * @return Etiqueta localizada correspondiente.
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
     * @param title   Título del diálogo.
     * @param message Mensaje a mostrar.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
