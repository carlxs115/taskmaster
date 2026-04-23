package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.DateFormatManager;
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
    @FXML private ActivityLogSectionController activityLogSectionController;
    @FXML private Label totalHoursLabel;
    @FXML private VBox workLogContainer;
    @FXML private Label emptyWorkLogLabel;
    @FXML private VBox subtasksSection;

    private boolean isSubtask = false;
    private Runnable onClose;
    private JsonNode taskData;
    private Runnable onTaskChanged;
    private java.util.function.Consumer<JsonNode> onOpenSubtaskDetail;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    public void setOnTaskChanged(Runnable callback) {
        this.onTaskChanged = callback;
    }

    public void initData(JsonNode task) {
        this.taskData = task;
        loadTaskDetail();
        Long taskId = task.path("id").asLong();
        activityLogSectionController.loadForEntity("TASK", taskId, "SUBTASK");
    }

    public void initDataAsSubtask(JsonNode subtask) {
        this.taskData = subtask;
        this.isSubtask = true;
        loadTaskDetail();
        Long subtaskId = subtask.path("id").asLong();
        activityLogSectionController.loadForEntity("SUBTASK", subtaskId);
        subtasksSection.setVisible(false);
        subtasksSection.setManaged(false);
    }

    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    public void setOnOpenSubtaskDetail(java.util.function.Consumer<JsonNode> callback) {
        this.onOpenSubtaskDetail = callback;
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
        descriptionLabel.setText(desc.isEmpty() ? lm.get("common.no.description") : desc);

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

                dueDateLabel.setText("📅 " + DateFormatManager.getInstance().format(due));

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
        loadTotalHours(taskId);
        loadWorkLogs(taskId);
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
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("task.detail.subtask.error.load")));
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
        subtaskProgressLabel.setText(
                java.text.MessageFormat.format(lm.get("task.detail.subtasks.progress"), done, total));

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

        titleLabel.setOnMouseClicked(e -> openSubtaskDetail(stId));
        titleLabel.setStyle(isDone
                ? "-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true; -fx-cursor: hand;"
                : "-fx-font-size: 13px; -fx-text-fill: #1e1e2e; -fx-cursor: hand;");


        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label priBadge = new Label(translatePriority(priority));
        priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

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
            MenuItem edit   = new MenuItem(lm.get("common.menu.edit"));
            edit.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10;");
            edit.setOnAction(ev -> openEditSubtask(stId, parentTaskId));
            MenuItem delete = new MenuItem(lm.get("common.menu.delete"));
            delete.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10; -fx-text-fill: #e74c3c;");
            delete.setOnAction(ev -> deleteSubtask(stId, parentTaskId));
            menu.getItems().addAll(edit, delete);
            menu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });

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
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(check, titleLabel, priBadge, spacer, menuBtn);
        return row;
    }

    private void openSubtaskDetail(Long subtaskId) {
        try {
            HttpResponse<String> resp = AppContext.getInstance().getApiService()
                    .get("/api/tasks/" + subtaskId);
            if (resp.statusCode() != 200) return;
            JsonNode subtask = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .readTree(resp.body());
            if (onOpenSubtaskDetail != null) {
                onOpenSubtaskDetail.accept(subtask);
                return;
            }
            // fallback
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            TaskDetailController controller = loader.getController();
            controller.initDataAsSubtask(subtask);
            Stage dialog = new Stage();
            dialog.setTitle(lm.get("task.detail.subtask.detail"));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(taskTitleLabel.getScene().getWindow());

            Scene scene = new Scene(root);
            applyThemeToScene(scene);
            dialog.setScene(scene);

            dialog.showAndWait();
            Long parentTaskId = taskData.get("id").asLong();
            loadSubtasks(parentTaskId);
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("error.open.subtask.detail"));
        }
    }

    private void openEditSubtask(Long subtaskId, Long parentTaskId) {
        try {
            HttpResponse<String> resp = AppContext.getInstance().getApiService()
                    .get("/api/tasks/" + subtaskId);
            if (resp.statusCode() != 200) return;
            JsonNode subtask = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .readTree(resp.body());

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(subtask);
            controller.setDialogTitle(lm.get("task.detail.subtask.edit"));
            controller.setOnTaskUpdated(() -> {
                loadSubtasks(parentTaskId);
                // Recargar historial dentro del callback, cuando el backend ya ha guardado
                Long taskId = taskData.get("id").asLong();
                activityLogSectionController.loadForEntity("TASK", taskId, "SUBTASK");
            });

            showAsDialog(root, lm.get("task.detail.subtask.edit"));
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("task.detail.subtask.error.edit"));
        }
    }

    private void deleteSubtask(Long subtaskId, Long parentTaskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("task.detail.subtask.delete.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("task.detail.subtask.delete.content"));
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
                        Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("task.detail.subtask.error.delete")));
                    }
                }).start();
            }
        });
    }

    // ── WorkLog ───────────────────────────────────────────────────────────────

    private void loadWorkLogs(Long taskId) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/worklogs/task/" + taskId);
                if (response.statusCode() == 200) {
                    JsonNode logs = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderWorkLogs(logs));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("task.detail.worklog.error.load")));
            }
        }).start();
    }

    private void renderWorkLogs(JsonNode logs) {
        workLogContainer.getChildren().clear();
        if (!logs.isArray() || logs.isEmpty()) {
            emptyWorkLogLabel.setVisible(true);
            emptyWorkLogLabel.setManaged(true);
            return;
        }
        emptyWorkLogLabel.setVisible(false);
        emptyWorkLogLabel.setManaged(false);

        for (JsonNode log : logs) {
            Long   logId        = log.path("id").asLong();
            String date         = log.path("date").asText().substring(0, 10);
            String activityType = translateActivityType(log.path("activityType").asText());
            String hours        = log.path("hours").asText();
            String note         = log.path("note").isNull() ? "" : log.path("note").asText();

            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 7 0 7 0; -fx-border-color: transparent transparent #f0f0f0 transparent; " +
                    "-fx-border-width: 0 0 1 0;");

            Label dateLbl = new Label(date);
            dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888; -fx-min-width: 85px;");

            Label typeLbl = new Label(activityType);
            typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                    "-fx-background-radius: 10px; -fx-background-color: #ede9fe; -fx-text-fill: #5b21b6;");

            Label hoursLbl = new Label(hours + "h");
            hoursLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e1e2e; " +
                    "-fx-min-width: 40px; -fx-alignment: CENTER_RIGHT;");

            Label noteLbl = new Label(note);
            noteLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #555555;");
            noteLbl.setWrapText(true);

            Button menuBtn = new Button("•••");
            menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666688; " +
                    "-fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; " +
                    "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;");
            menuBtn.setOnMouseEntered(e -> menuBtn.setStyle(
                    "-fx-background-color: #f0f0f5; -fx-text-fill: #1e1e2e; " +
                            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;"));
            menuBtn.setOnMouseExited(e -> menuBtn.setStyle(
                    "-fx-background-color: transparent; -fx-text-fill: #666688; " +
                            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;"));

            Long taskId = taskData.get("id").asLong();

            menuBtn.setOnAction(e -> {
                ContextMenu menu = new ContextMenu();
                menu.setStyle("-fx-background-color: white; -fx-border-color: #e8e8e8; " +
                        "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

                MenuItem editItem   = new MenuItem(lm.get("common.menu.edit"));
                editItem.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10;");
                editItem.setOnAction(ev -> openEditWorkLog(logId, log, taskId));

                MenuItem deleteItem = new MenuItem(lm.get("common.menu.delete"));
                deleteItem.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10; -fx-text-fill: #e74c3c;");
                deleteItem.setOnAction(ev -> deleteWorkLog(logId, taskId));

                menu.getItems().addAll(editItem, deleteItem);
                menu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            row.getChildren().addAll(dateLbl, hoursLbl, typeLbl, noteLbl, spacer, menuBtn);
            workLogContainer.getChildren().add(row);
        }
    }

    private void openEditWorkLog(Long logId, JsonNode log, Long taskId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/add-worklog-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            AddWorkLogController controller = loader.getController();
            controller.setTaskId(taskId);
            controller.initData(logId, log);
            controller.setOnWorkLogAdded(() -> {
                loadWorkLogs(taskId);
                loadTotalHours(taskId);
            });
            showAsDialog(root, lm.get("task.detail.worklog.edit"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("task.detail.worklog.error.open"));
        }
    }

    private void deleteWorkLog(Long logId, Long taskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("task.detail.worklog.delete.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("task.detail.worklog.delete.content"));
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        AppContext.getInstance().getApiService()
                                .delete("/api/worklogs/" + logId);
                        Platform.runLater(() -> {
                            loadWorkLogs(taskId);
                            loadTotalHours(taskId);
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("task.detail.worklog.error.delete")));
                    }
                }).start();
            }
        });
    }

    private void loadTotalHours(Long taskId) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/worklogs/task/" + taskId + "/total");
                if (response.statusCode() == 200) {
                    String total = response.body();
                    Platform.runLater(() ->
                            totalHoursLabel.setText(java.text.MessageFormat.format(lm.get("task.detail.worklog.total"), total)));
                }
            } catch (Exception e) {
                // silencioso, no es crítico
            }
        }).start();
    }

    @FXML
    private void handleAddWorkLog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/add-worklog-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            AddWorkLogController controller = loader.getController();
            Long taskId = taskData.get("id").asLong();
            controller.setTaskId(taskId);
            controller.setOnWorkLogAdded(() -> {
                loadWorkLogs(taskId);
                loadTotalHours(taskId);
            });
            showAsDialog(root, lm.get("common.worklog.add.title"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    private String translateActivityType(String type) {
        String key = "activity." + type;
        String val = lm.get(key);
        return val.startsWith("?") ? type : val;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────
    @FXML
    private void handleNewSubtask() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-subtask-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            NewSubtaskController controller = loader.getController();

            Long parentId   = taskData.get("id").asLong();
            Long projectId  = taskData.has("projectId") && !taskData.get("projectId").isNull()
                    ? taskData.get("projectId").asLong() : null;
            String category = taskData.has("category") ? taskData.get("category").asText() : "PERSONAL";

            controller.initData(parentId, projectId, category);
            controller.setOnSubtaskCreated(() -> {
                loadSubtasks(parentId);
                // Recargar historial al crear subtarea
                activityLogSectionController.loadForEntity("TASK", parentId, "SUBTASK");
                if (onTaskChanged != null) onTaskChanged.run();
            });
            showAsDialog(root, lm.get("new.subtask.title"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    @FXML
    private void handleEdit() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(taskData);
            controller.setDialogTitle(isSubtask
                    ? lm.get("task.detail.subtask.edit")
                    : lm.get("common.menu.edit"));

            controller.setOnTaskUpdated(() -> {
                Long taskId = taskData.get("id").asLong();
                new Thread(() -> {
                    try {
                        HttpResponse<String> resp = AppContext.getInstance()
                                .getApiService().get("/api/tasks/" + taskId);
                        if (resp.statusCode() == 200) {
                            taskData = objectMapper.readTree(resp.body());
                            Platform.runLater(() -> {
                                loadTaskDetail();
                                // Recargar historial de actividad en tiempo real
                                if (isSubtask) {
                                    activityLogSectionController.loadForEntity("SUBTASK", taskId);
                                } else {
                                    activityLogSectionController.loadForEntity("TASK", taskId, "SUBTASK");
                                }
                                if (onTaskChanged != null) onTaskChanged.run();
                            });
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            });

            showAsDialog(root, isSubtask
                    ? lm.get("task.detail.subtask.edit")
                    : lm.get("common.menu.edit"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    @FXML
    private void handleClose() {
        closeDialog();
    }

    private void closeDialog() {
        if (onClose != null) {
            onClose.run();
        } else {
            ((Stage) taskTitleLabel.getScene().getWindow()).close();
        }
    }

    private void showAsDialog(VBox root, String title) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(taskTitleLabel.getScene().getWindow());
        Scene scene = new Scene(root);
        applyThemeToScene(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void applyThemeToScene(Scene scene) {
        com.taskmaster.taskmasterfrontend.util.ThemeManager tm =
                com.taskmaster.taskmasterfrontend.util.ThemeManager.getInstance();
        String cssFile = "/com/taskmaster/taskmasterfrontend/themes/"
                + tm.getCssFileNamePublic();
        String cssUrl = getClass().getResource(cssFile) != null
                ? getClass().getResource(cssFile).toExternalForm()
                : null;
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl);
        }
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
            case "TODO"        -> lm.get("status.TODO");
            case "IN_PROGRESS" -> lm.get("status.IN_PROGRESS");
            case "DONE"        -> lm.get("status.DONE");
            case "SUBMITTED"   -> lm.get("status.SUBMITTED");
            case "CANCELLED"   -> lm.get("status.CANCELLED");
            default            -> status;
        };
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
