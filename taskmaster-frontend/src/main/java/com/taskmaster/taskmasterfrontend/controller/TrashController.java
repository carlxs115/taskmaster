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
import javafx.scene.layout.VBox;

import java.net.http.HttpResponse;

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

    public void setOnTrashChanged(Runnable callback) {
        this.onTrashChanged = callback;
    }

    @FXML
    public void initialize() {
        loadTrashTasks();
        loadTrashProjects();
        loadRetentionDays();
    }

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

    private HBox createTrashTaskCard(JsonNode task) {
        HBox card = new HBox();
        card.setSpacing(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 16; " +
                "-fx-background-radius: 6px; -fx-border-color: #e0e0e0; " +
                "-fx-border-radius: 6px;");

        Long taskId = task.get("id").asLong();
        String title = task.get("title").asText();
        String priority = task.get("priority").asText();

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label priorityBadge = new Label(translatePriority(priority));
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: #aaaaaa;");

        Button restoreBtn = new Button(lm.get("trash.restore"));
        restoreBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; " +
                "-fx-background-radius: 4px; -fx-cursor: hand; -fx-font-size: 12px;");
        restoreBtn.setOnAction(e -> restoreTask(taskId));

        Button deleteBtn = new Button(lm.get("trash.delete"));
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-background-radius: 4px; -fx-cursor: hand; -fx-font-size: 12px;");
        deleteBtn.setOnAction(e -> permanentlyDeleteTask(taskId));

        card.getChildren().addAll(titleLabel, priorityBadge, restoreBtn, deleteBtn);
        return card;
    }

    private HBox createTrashProjectCard(JsonNode project) {
        HBox card = new HBox();
        card.setSpacing(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 16; " +
                "-fx-background-radius: 6px; -fx-border-color: #e0e0e0; " +
                "-fx-border-radius: 6px;");

        Long projectId = project.get("id").asLong();
        String name = project.get("name").asText();
        String category = project.get("category").asText();

        Label nameLabel = new Label("📁 " + name);
        nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label categoryBadge = new Label(translateCategory(category));
        categoryBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: #aaaaaa;");

        Button restoreBtn = new Button(lm.get("trash.restore"));
        restoreBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; " +
                "-fx-background-radius: 4px; -fx-cursor: hand; -fx-font-size: 12px;");
        restoreBtn.setOnAction(e -> restoreProject(projectId));

        Button deleteBtn = new Button(lm.get("trash.delete"));
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-background-radius: 4px; -fx-cursor: hand; -fx-font-size: 12px;");
        deleteBtn.setOnAction(e -> permanentlyDeleteProject(projectId));

        card.getChildren().addAll(nameLabel, categoryBadge, restoreBtn, deleteBtn);
        return card;
    }

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

    public void refresh() {
        loadTrashTasks();
        loadTrashProjects();
    }

    private String translatePriority(String priority) {
        return switch (priority) {
            case "LOW"    -> lm.get("priority.LOW");
            case "MEDIUM" -> lm.get("priority.MEDIUM");
            case "HIGH"   -> lm.get("priority.HIGH");
            case "URGENT" -> lm.get("priority.URGENT");
            default       -> priority;
        };
    }

    private String translateCategory(String category) {
        return switch (category) {
            case "PERSONAL" -> lm.get("category.PERSONAL");
            case "ESTUDIOS" -> lm.get("category.ESTUDIOS");
            case "TRABAJO"  -> lm.get("category.TRABAJO");
            default         -> category;
        };
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
