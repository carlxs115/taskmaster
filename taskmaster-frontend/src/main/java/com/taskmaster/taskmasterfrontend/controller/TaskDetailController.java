package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TaskDetailController {

    @FXML private Label taskIdLabel;
    @FXML private Label taskTitleLabel;
    @FXML private Label statusBadge;
    @FXML private Label priorityBadge;
    @FXML private Label categoryBadge;
    @FXML private Label dueDateLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label subtaskProgressLabel;
    @FXML private Label emptySubtasksLabel;
    @FXML private VBox subtaskContainer;
    @FXML private StackPane progressBarBg;
    @FXML private StackPane progressBarFill;

    private JsonNode taskData;
    private Runnable onTaskChanged;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public void setOnTaskChanged(Runnable callback) {
        this.onTaskChanged = callback;
    }

    public void initData(JsonNode task) {
        this.taskData = task;
        loadTaskDetail();
    }

    // ── Carga principal ───────────────────────────────────────────────────────
    private void loadTaskDetail() {
        String status = taskData.get("status").asText();
        String priority = taskData.get("priority").asText();
        String category = taskData.has("category") ? taskData.get("category").asText() : "PERSONAL";
        String title = taskData.get("title").asText();
        String desc = taskData.has("description") && !taskData.get("description").isNull()
                ? taskData.get("description").asText() : "";
        Long taskId = taskData.get("id").asLong();

        taskIdLabel.setText("#" + taskId);
        taskTitleLabel.setText(title);
        descriptionLabel.setText(desc.isEmpty() ? "Sin descripción" : desc);

        // Status badge
        statusBadge.setText(translateStatus(status));
        statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getStatusColor(status) + ";");

        // Priority badge
        priorityBadge.setText(translatePriority(priority));
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        // Category badge
        categoryBadge.setText(category);
        categoryBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; " +
                "-fx-background-radius: 10px; " + getCategoryBadgeStyle(category));

        // Due date
        if (taskData.has("dueDate") && !taskData.get("dueDate").isNull()) {
            try {
                LocalDate due = LocalDate.parse(taskData.get("dueDate").asText().substring(0, 10));

                boolean overdue = due.isBefore(LocalDate.now())
                        && !"DONE".equals(status) && !"CANCELLED".equals(status);

                dueDateLabel.setText("📅 " + due.format(
                        DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "ES"))));

                if (overdue) {
                    dueDateLabel.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; " +
                            "-fx-background-radius: 10px; -fx-text-fill: #991b1b; " +
                            "-fx-background-color: #fee2e2;");
                }

                dueDateLabel.setVisible(true);
                dueDateLabel.setManaged(true);

            } catch (Exception ignored) {
                dueDateLabel.setVisible(false);
                dueDateLabel.setManaged(false);
            }
        } else {
            dueDateLabel.setVisible(false);
            dueDateLabel.setManaged(false);
        }
        loadSubtasks(taskId);
    }

    // ── Subtareas ─────────────────────────────────────────────────────────────
    private void loadSubtasks(Long taskId) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks/" + taskId + "/subtasks");
                if (response.statusCode() == 200) {
                    JsonNode subtasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderSubtasks(subtasks, taskId));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las subtareas"));
            }
        }).start();
    }

    private void renderSubtasks(JsonNode subtasks, Long parentTaskId) {
        subtaskContainer.getChildren().clear();

        if (!subtasks.isArray() || subtasks.isEmpty()) {
            emptySubtasksLabel.setVisible(true);
            emptySubtasksLabel.setManaged(true);
            subtaskProgressLabel.setText("0/0");
            progressBarBg.setVisible(false);
            progressBarBg.setManaged(false);
            return;
        }

        emptySubtasksLabel.setVisible(false);
        emptySubtasksLabel.setManaged(false);

        // Calcular progreso
        int total = subtasks.size();
        int done  = 0;
        for (JsonNode st : subtasks) {
            if ("DONE".equals(st.has("status") ? st.get("status").asText() : "")) done++;
        }
        subtaskProgressLabel.setText(done + "/" + total + " completadas");

        // Barra de progreso
        progressBarBg.setVisible(true);
        progressBarBg.setManaged(true);
        double pct = (double) done / total;
        progressBarBg.widthProperty().addListener((obs, o, n) ->
                progressBarFill.setPrefWidth(n.doubleValue() * pct));

        // Renderizar subtareas
        for (JsonNode subtask : subtasks) {
            subtaskContainer.getChildren().add(createSubtaskRow(subtask, parentTaskId));
        }
    }

    private HBox createSubtaskRow(JsonNode subtask, Long parentTaskId) {
        String status  = subtask.get("status").asText();
        String title   = subtask.get("title").asText();
        String priority = subtask.has("priority") ? subtask.get("priority").asText() : "MEDIUM";
        Long   stId    = subtask.get("id").asLong();
        boolean isDone = "DONE".equals(status);

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 8 0 8 0; -fx-border-color: transparent transparent #f0f0f0 transparent; " +
                "-fx-border-width: 0 0 1 0;");

        CheckBox check = new CheckBox();
        check.setSelected(isDone);

        Label titleLabel = new Label(title);
        titleLabel.setStyle(isDone
                ? "-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true;"
                : "-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label priBadge = new Label(translatePriority(priority));
        priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 13px;");
        deleteBtn.setOnAction(e -> deleteSubtask(stId, parentTaskId));

        final boolean[] updating = {false};
        check.selectedProperty().addListener((obs, was, is) -> {
            if (updating[0]) return;
            String newStatus = is ? "DONE" : "TODO";
            new Thread(() -> {
                try {
                    HttpResponse<String> resp = AppContext.getInstance().getApiService()
                            .patch("/api/tasks/" + stId + "/status?status=" + newStatus, null);
                    Platform.runLater(() -> {
                        if (resp.statusCode() == 200) {
                            updating[0] = true;
                            titleLabel.setStyle(is
                                    ? "-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true;"
                                    : "-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
                            updating[0] = false;
                            // Recargar para actualizar progreso
                            loadSubtasks(parentTaskId);
                            if (onTaskChanged != null) onTaskChanged.run();
                        } else {
                            updating[0] = true;
                            check.setSelected(was);
                            updating[0] = false;
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> check.setSelected(was));
                }
            }).start();
        });
        row.getChildren().addAll(check, titleLabel, priBadge, deleteBtn);
        return row;
    }

    private void deleteSubtask(Long subtaskId, Long parentTaskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar subtarea");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Seguro que quieres eliminar esta subtarea?");
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        AppContext.getInstance().getApiService()
                                .delete("/api/tasks/" + subtaskId);
                        Platform.runLater(() -> {
                            loadSubtasks(parentTaskId);
                            if (onTaskChanged != null) onTaskChanged.run();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("Error", "No se pudo eliminar la subtarea"));
                    }
                }).start();
            }
        });
    }

    // ── Acciones ──────────────────────────────────────────────────────────────
    @FXML
    private void handleNewSubtask() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-subtask-dialog.fxml"));
            VBox root = loader.load();
            NewSubtaskController controller = loader.getController();

            Long parentId   = taskData.get("id").asLong();
            Long projectId  = taskData.has("projectId") && !taskData.get("projectId").isNull()
                    ? taskData.get("projectId").asLong() : null;
            String category = taskData.has("category") ? taskData.get("category").asText() : "PERSONAL";

            controller.initData(parentId, projectId, category);
            controller.setOnSubtaskCreated(() -> {
                loadSubtasks(parentId);
                if (onTaskChanged != null) onTaskChanged.run();
            });
            Stage dialog = new Stage();
            dialog.setTitle("Nueva subtarea");
            dialog.setScene(new Scene(root, 450, 320));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    @FXML
    private void handleEdit() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"));
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(taskData);
            controller.setOnTaskUpdated(() -> {
                if (onTaskChanged != null) onTaskChanged.run();
                closeDialog();
            });
            Stage dialog = new Stage();
            dialog.setTitle("Editar tarea");
            dialog.setScene(new Scene(root, 500, 420));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    @FXML
    private void handleClose() {
        closeDialog();
    }

    private void closeDialog() {
        ((Stage) taskTitleLabel.getScene().getWindow()).close();
    }


    // ── Colores ───────────────────────────────────────────────────────────────
    private String getStatusColor(String s) {
        return switch (s) {
            case "TODO"        -> "#95a5a6";
            case "IN_PROGRESS" -> "#3498db";
            case "DONE"        -> "#2ecc71";
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
