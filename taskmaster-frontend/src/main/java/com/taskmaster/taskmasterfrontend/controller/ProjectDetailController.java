package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.http.HttpResponse;
import java.time.LocalDate;

public class ProjectDetailController {

    @FXML private Label projectIdLabel;
    @FXML private Label projectNameLabel;
    @FXML private Label statusBadge;
    @FXML private Label priorityBadge;
    @FXML private Label categoryBadge;
    @FXML private Label descriptionLabel;
    @FXML private Label statTotalNum;
    @FXML private Label statPendingNum;
    @FXML private Label statDoneNum;
    @FXML private Label statOverdueNum;
    @FXML private Label progressLabel;
    @FXML private StackPane progressBarBg;
    @FXML private StackPane progressBarFill;
    @FXML private VBox taskListContainer;
    @FXML private Label emptyTasksLabel;
    @FXML private ActivityLogSectionController activityLogSectionController;

    private Runnable onClose;
    private JsonNode projectData;
    private java.util.function.Consumer<JsonNode> onOpenTaskDetail;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    public void initData(JsonNode project) {
        this.projectData = project;
        loadProjectDetail();
        Long projectId = project.path("id").asLong();
        activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
    }

    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    public void setOnOpenTaskDetail(java.util.function.Consumer<JsonNode> callback) {
        this.onOpenTaskDetail = callback;
    }

    private void loadProjectDetail() {
        Long   id       = projectData.get("id").asLong();
        String name     = projectData.get("name").asText();
        String status   = projectData.has("status")   && !projectData.get("status").isNull()
                ? projectData.get("status").asText()   : "TODO";
        String priority = projectData.has("priority") && !projectData.get("priority").isNull()
                ? projectData.get("priority").asText() : "MEDIUM";
        String category = projectData.has("category") && !projectData.get("category").isNull()
                ? projectData.get("category").asText() : "PERSONAL";
        String desc     = projectData.has("description") && !projectData.get("description").isNull()
                ? projectData.get("description").asText() : "";

        projectIdLabel.setText("#" + id);
        projectNameLabel.setText(name);
        descriptionLabel.setText(desc.isEmpty() ? lm.get("common.no.description") : desc);

        statusBadge.setText(translateStatus(status));
        statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getStatusColor(status) + ";");

        priorityBadge.setText(translatePriority(priority));
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        categoryBadge.setText(category);
        categoryBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; " +
                "-fx-background-radius: 10px; " + getCategoryBadgeStyle(category));

        loadTasks(id);
    }

    private void loadTasks(Long projectId) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks?projectId=" + projectId);
                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    private void renderTasks(JsonNode tasks) {
        taskListContainer.getChildren().clear();

        if (!tasks.isArray() || tasks.isEmpty()) {
            emptyTasksLabel.setVisible(true);
            emptyTasksLabel.setManaged(true);
            statTotalNum.setText("0");
            statPendingNum.setText("0");
            statDoneNum.setText("0");
            statOverdueNum.setText("0");
            progressLabel.setText("0%");
            progressBarFill.setPrefWidth(0);
            return;
        }

        emptyTasksLabel.setVisible(false);
        emptyTasksLabel.setManaged(false);

        int total = 0, pending = 0, done = 0, overdue = 0;
        LocalDate today = LocalDate.now();
        for (JsonNode task : tasks) {
            total++;
            String s = task.has("status") ? task.get("status").asText() : "TODO";

            if ("DONE".equals(s)) {
                done++;
            } else if ("DONE".equals(s) || "IN_PROGRESS".equals(s)) {
                pending++;

                if (task.has("dueDate") && !task.get("dueDate").isNull()) {
                    try {
                        LocalDate due = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                        if (due.isBefore(today)) {
                            overdue++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        statTotalNum.setText(String.valueOf(total));
        statPendingNum.setText(String.valueOf(pending));
        statDoneNum.setText(String.valueOf(done));
        statOverdueNum.setText(String.valueOf(overdue));

        double pct = total > 0 ? (double) done / total * 100 : 0;
        progressLabel.setText(Math.round(pct) + "%");
        progressBarBg.widthProperty().addListener((obs, o, n) ->
                progressBarFill.setPrefWidth(n.doubleValue() * pct / 100.0));

        // Renderizar lista de tareas
        int idx = 0;
        for (JsonNode task : tasks) {
            if (idx++ > 0) {
                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: #f0f0f0;");
                taskListContainer.getChildren().add(sep);
            }
            taskListContainer.getChildren().add(createTaskRow(task));
        }
    }

    private HBox createTaskRow(JsonNode task) {
        String status   = task.has("status")   ? task.get("status").asText()   : "TODO";
        String title    = task.get("title").asText();
        String priority = task.has("priority") ? task.get("priority").asText() : "MEDIUM";
        boolean isDone  = "DONE".equals(status);

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 8 0 8 0;");

        // Indicador de estado como círculo de color
        Label statusDot = new Label("●");
        statusDot.setStyle("-fx-text-fill: " + getStatusColor(status) + "; -fx-font-size: 10px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle(isDone
                ? "-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true;"
                : "-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");

        // Badge fecha vencida
        boolean isOverdue = false;
        if (task.has("dueDate") && !task.get("dueDate").isNull() && !isDone) {
            try {
                LocalDate due = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                isOverdue = due.isBefore(LocalDate.now());
            } catch (Exception ignored) {}
        }

        Label priBadge = new Label(priority);
        priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        row.getChildren().addAll(statusDot, titleLabel);

        if (isOverdue) {
            Label overdueLabel = new Label("Vencida");
            overdueLabel.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                    "-fx-background-radius: 10px; -fx-text-fill: #991b1b; " +
                    "-fx-background-color: #fee2e2;");
            row.getChildren().add(overdueLabel);
        }
        row.getChildren().add(priBadge);

        Long taskId = task.path("id").asLong();

        Button menuBtn = new Button("•••");
        menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666688; " +
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; " +
                "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;");
        menuBtn.setOnMouseEntered(e -> menuBtn.setStyle(
                "-fx-background-color: #f0f0f5; -fx-text-fill: #1e1e2e; " +
                        "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; " +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;"));
        menuBtn.setOnMouseExited(e -> menuBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #666688; " +
                        "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; " +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;"));
        menuBtn.setOnAction(e -> {
            ContextMenu menu = new ContextMenu();
            menu.setStyle("-fx-background-color: white; -fx-border-color: #e8e8e8; " +
                    "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");
            MenuItem detail = new MenuItem(lm.get("common.menu.detail"));
            detail.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10;");
            detail.setOnAction(ev -> openTaskDetail(task, taskId));
            MenuItem edit = new MenuItem(lm.get("common.menu.edit"));
            edit.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10;");
            edit.setOnAction(ev -> openEditTask(task, taskId));
            MenuItem delete = new MenuItem(lm.get("common.menu.delete"));
            delete.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10; -fx-text-fill: #e74c3c;");
            delete.setOnAction(ev -> deleteTask(taskId));
            menu.getItems().addAll(detail, edit, delete);
            menu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);
        row.getChildren().add(menuBtn);

        return row;
    }

    private void openTaskDetail(JsonNode task, Long taskId) {
        if (onOpenTaskDetail != null) {
            onOpenTaskDetail.accept(task);
            return;
        }
        // fallback
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"));
            VBox root = loader.load();
            TaskDetailController controller = loader.getController();
            controller.initData(task);
            showAsDialog(root, lm.get("project.detail.task.detail"));
            Long projectId = projectData.path("id").asLong();
            activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
            loadTasks(projectId);
        } catch (Exception e) {
            showAlert("Error", "No se pudo abrir el detalle de la tarea");
        }
    }

    private void openEditTask(JsonNode task, Long taskId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"));
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(task);
            controller.setOnTaskUpdated(() -> {
                Long projectId = projectData.path("id").asLong();
                loadTasks(projectId);
                activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
            });
            showAsDialog(root, lm.get("project.detail.task.edit"));
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("project.detail.task.editor.error"));
        }
    }

    private void deleteTask(Long taskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("common.delete.task.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("project.detail.delete.content"));
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        AppContext.getInstance().getApiService()
                                .delete("/api/tasks/" + taskId);
                        Platform.runLater(() -> {
                            Long projectId = projectData.path("id").asLong();
                            loadTasks(projectId);
                            activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.delete.task")));
                    }
                }).start();
            }
        });
    }

    @FXML
    private void handleClose() {
        if (onClose != null) {
            onClose.run();
        } else {
            ((Stage) projectNameLabel.getScene().getWindow()).close();
        }
    }

    private void showAsDialog(VBox root, String title) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(projectNameLabel.getScene().getWindow());
        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    // ── Colores ───────────────────────────────────────────────────────────────
    private String getStatusColor(String s) {
        return switch (s) {
            case "TODO"        -> "#95a5a6";
            case "IN_PROGRESS" -> "#3498db";
            case "DONE"        -> "#2ecc71";
            case "SUBMITTED" -> "#8e44ad";
            case "CANCELLED"   -> "#e74c3c";
            default            -> "#95a5a6";
        };
    }

    private String getPriorityColor(String p) {
        return switch (p) {
            case "URGENT" -> "#e74c3c";
            case "HIGH"   -> "#e67e22";
            case "MEDIUM" -> "#3498db";
            case "LOW"    -> "#95a5a6";
            default       -> "#95a5a6";
        };
    }

    private String getCategoryBadgeStyle(String c) {
        return switch (c) {
            case "PERSONAL" -> "-fx-background-color: #f3e8ff; -fx-text-fill: #6b21a8;";
            case "ESTUDIOS" -> "-fx-background-color: #fef3c7; -fx-text-fill: #92400e;";
            case "TRABAJO"  -> "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af;";
            default         -> "-fx-background-color: #f0f0f5; -fx-text-fill: #666666;";
        };
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "TODO"        -> "PENDIENTE";
            case "IN_PROGRESS" -> "EN CURSO";
            case "DONE"        -> "COMPLETADA";
            case "SUBMITTED" -> "ENTREGADA";
            case "CANCELLED"   -> "CANCELADA";
            default            -> status;
        };
    }

    private String translatePriority(String priority) {
        return switch (priority) {
            case "LOW"    -> "BAJA";
            case "MEDIUM" -> "MEDIA";
            case "HIGH"   -> "ALTA";
            case "URGENT" -> "URGENTE";
            default       -> priority;
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
