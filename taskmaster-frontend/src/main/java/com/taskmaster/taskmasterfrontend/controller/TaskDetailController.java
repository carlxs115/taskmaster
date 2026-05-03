package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.DateFormatManager;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.MenuButtonFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Controlador de la vista de detalle de tarea.
 *
 * <p>Muestra la información completa de una tarea o subtarea: título, estado,
 * prioridad, categoría, fecha límite, descripción, subtareas con barra de
 * progreso, registros de trabajo (worklogs) y el historial de actividad.
 * Permite editar la tarea, crear subtareas, gestionar worklogs y abrir el
 * detalle de una subtarea concreta.</p>
 *
 * @author Carlos
 */
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

    /**
     * Registra el callback que se ejecutará cuando la tarea o alguna de sus
     * subtareas cambie de estado, para que el controlador padre pueda refrescar su vista.
     *
     * @param callback Acción a ejecutar al detectar un cambio.
     */
    public void setOnTaskChanged(Runnable callback) {
        this.onTaskChanged = callback;
    }

    /**
     * Carga los datos de una tarea en la vista e inicializa el historial
     * de actividad de la tarea y sus subtareas.
     *
     * @param task Nodo JSON con los datos de la tarea a mostrar.
     */
    public void initData(JsonNode task) {
        this.taskData = task;
        loadTaskDetail();
        Long taskId = task.path("id").asLong();
        activityLogSectionController.loadForEntity("TASK", taskId, "SUBTASK");
    }

    /**
     * Carga los datos de una subtarea en la vista, inicializa su historial
     * de actividad y oculta la sección de subtareas, ya que las subtareas
     * no pueden tener subtareas propias.
     *
     * @param subtask Nodo JSON con los datos de la subtarea a mostrar.
     */
    public void initDataAsSubtask(JsonNode subtask) {
        this.taskData = subtask;
        this.isSubtask = true;
        loadTaskDetail();
        Long subtaskId = subtask.path("id").asLong();
        activityLogSectionController.loadForEntity("SUBTASK", subtaskId);
        subtasksSection.setVisible(false);
        subtasksSection.setManaged(false);
    }

    /**
     * Registra el callback que se ejecutará al cerrar la vista de detalle.
     *
     * @param callback Acción a ejecutar al cerrar.
     */
    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    /**
     * Registra el callback que se ejecutará al abrir el detalle de una subtarea,
     * permitiendo que el controlador padre gestione la navegación.
     *
     * @param callback Consumidor que recibe el nodo JSON de la subtarea seleccionada.
     */
    public void setOnOpenSubtaskDetail(java.util.function.Consumer<JsonNode> callback) {
        this.onOpenSubtaskDetail = callback;
    }

    /**
     * Rellena todos los campos de la vista con los datos del nodo JSON de la tarea
     * e inicia la carga asíncrona de subtareas, total de horas y worklogs.
     */
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

    /**
     * Obtiene las subtareas de la tarea desde el backend y las renderiza en la vista.
     *
     * @param taskId Identificador de la tarea padre.
     */
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

    /**
     * Renderiza la lista de subtareas, calcula el progreso completado
     * y actualiza la barra de progreso y la etiqueta de contador.
     *
     * @param subtasks     Array JSON con las subtareas de la tarea.
     * @param parentTaskId Identificador de la tarea padre.
     */
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

    /**
     * Construye la fila visual de una subtarea con checkbox de estado,
     * título, badge de prioridad y menú de acciones (editar/eliminar).
     *
     * <p>El checkbox actualiza el estado de la subtarea en el backend al pulsarlo
     * y recarga la lista de subtareas si la operación es exitosa.</p>
     *
     * @param subtask      Nodo JSON con los datos de la subtarea.
     * @param parentTaskId Identificador de la tarea padre.
     * @return {@link HBox} con el contenido visual de la fila.
     */
    private HBox createSubtaskRow(JsonNode subtask, Long parentTaskId) {
        String status   = subtask.get("status").asText();
        String title    = subtask.get("title").asText();
        String priority = subtask.has("priority") ? subtask.get("priority").asText() : "MEDIUM";
        Long   stId     = subtask.get("id").asLong();
        boolean isDone  = "DONE".equals(status);

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-field-row");

        CheckBox check = new CheckBox();
        check.setSelected(isDone);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(isDone ? "task-title-done" : "task-title");
        titleLabel.setOnMouseClicked(e -> openSubtaskDetail(stId));
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label statusBadge = new Label(translateStatus(status));
        statusBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getStatusColor(status) + ";");

        Label priBadge = new Label(translatePriority(priority));
        priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        Button menuBtn = MenuButtonFactory.createEditDeleteMenu(
                lm.get("common.menu.edit"),
                lm.get("common.menu.delete"),
                () -> openEditSubtask(stId, parentTaskId),
                () -> deleteSubtask(stId, parentTaskId)
        );

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
                            titleLabel.getStyleClass().removeAll("task-title", "task-title-done");
                            titleLabel.getStyleClass().add(is ? "task-title-done" : "task-title");
                            updating[0] = false;
                            loadSubtasks(parentTaskId);
                            activityLogSectionController.loadForEntity("TASK", parentTaskId, "SUBTASK");
                            if (onTaskChanged != null) onTaskChanged.run();
                        } else {
                            updating[0] = true;
                            check.setSelected(was);
                            updating[0] = false;
                            showAlert(lm.get("error.title"), lm.get("task.error.pending.subtasks"));
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> check.setSelected(was));
                }
            }).start();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(check, titleLabel, statusBadge, priBadge, spacer, menuBtn);
        return row;
    }

    /**
     * Abre la vista de detalle de una subtarea. Si hay un callback registrado
     * lo usa para delegar la navegación; si no, abre un diálogo modal.
     *
     * @param subtaskId Identificador de la subtarea a mostrar.
     */
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

    /**
     * Abre el diálogo modal de edición de una subtarea y recarga la lista
     * de subtareas y el historial al guardar.
     *
     * @param subtaskId    Identificador de la subtarea a editar.
     * @param parentTaskId Identificador de la tarea padre.
     */
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

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * la subtarea del backend y recarga la lista de subtareas.
     *
     * @param subtaskId    Identificador de la subtarea a eliminar.
     * @param parentTaskId Identificador de la tarea padre.
     */
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
                            activityLogSectionController.loadForEntity("TASK", parentTaskId, "SUBTASK");
                            if (onTaskChanged != null) onTaskChanged.run();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("task.detail.subtask.error.delete")));
                    }
                }).start();
            }
        });
    }

    /**
     * Obtiene los registros de trabajo de la tarea desde el backend
     * y los renderiza en la vista.
     *
     * @param taskId Identificador de la tarea.
     */
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

    /**
     * Renderiza la lista de worklogs con fecha, horas, tipo de actividad,
     * nota y menú de acciones (editar/eliminar) para cada entrada.
     *
     * @param logs Array JSON con los registros de trabajo de la tarea.
     */
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
            row.getStyleClass().add("profile-field-row");

            Label dateLbl = new Label(date);
            dateLbl.getStyleClass().add("profile-field-label");
            dateLbl.setStyle("-fx-min-width: 85px;");

            Label typeLbl = new Label(activityType);
            typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                    "-fx-background-radius: 10px; " +
                    "-fx-background-color: -tm-bg-active; " +
                    "-fx-text-fill: -tm-accent-light;");

            Label hoursLbl = new Label(hours + "h");
            hoursLbl.getStyleClass().add("profile-field-value");
            hoursLbl.setStyle("-fx-font-weight: bold; -fx-min-width: 40px; -fx-alignment: CENTER_RIGHT;");

            Label noteLbl = new Label(note);
            noteLbl.getStyleClass().add("profile-field-value");
            noteLbl.setWrapText(true);

            Long taskId = taskData.get("id").asLong();
            Button menuBtn = MenuButtonFactory.createEditDeleteMenu(
                    lm.get("common.menu.edit"),
                    lm.get("common.menu.delete"),
                    () -> openEditWorkLog(logId, log, taskId),
                    () -> deleteWorkLog(logId, taskId)
            );

            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            row.getChildren().addAll(dateLbl, hoursLbl, typeLbl, noteLbl, spacer, menuBtn);
            workLogContainer.getChildren().add(row);
        }
    }

    /**
     * Abre el diálogo modal de edición de un worklog existente y recarga
     * la lista y el total de horas al guardar.
     *
     * @param logId  Identificador del worklog a editar.
     * @param log    Nodo JSON con los datos actuales del worklog.
     * @param taskId Identificador de la tarea a la que pertenece el worklog.
     */
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

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * el worklog del backend y recarga la lista y el total de horas.
     *
     * @param logId  Identificador del worklog a eliminar.
     * @param taskId Identificador de la tarea a la que pertenece el worklog.
     */
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

    /**
     * Obtiene el total de horas registradas para la tarea desde el backend
     * y actualiza la etiqueta correspondiente.
     *
     * @param taskId Identificador de la tarea.
     */
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

    /**
     * Abre el diálogo modal para añadir un nuevo worklog a la tarea
     * y recarga la lista y el total de horas al guardarlo.
     */
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

    /**
     * Traduce un código de tipo de actividad de worklog a su etiqueta localizada.
     *
     * @param type Código del tipo de actividad (p.ej. {@code "DEVELOPMENT"}).
     * @return Etiqueta localizada, o el propio código si no hay traducción.
     */
    private String translateActivityType(String type) {
        String key = "activity." + type;
        String val = lm.get(key);
        return val.startsWith("?") ? type : val;
    }

    /**
     * Abre el diálogo modal de creación de nueva subtarea y, al crearla,
     * recarga la lista de subtareas y el historial de actividad.
     */
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

    /**
     * Abre el diálogo modal de edición de la tarea o subtarea actual y,
     * tras guardar, recarga los datos desde el backend y actualiza la vista
     * y el historial de actividad.
     */
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

    /**
     * Cierra la vista de detalle ejecutando el callback de cierre si está
     * registrado, o cerrando la ventana directamente en caso contrario.
     */
    @FXML
    private void handleClose() {
        closeDialog();
    }

    /**
     * Ejecuta el callback de cierre si está registrado,
     * o cierra la ventana directamente en caso contrario.
     */
    private void closeDialog() {
        if (onClose != null) {
            onClose.run();
        } else {
            ((Stage) taskTitleLabel.getScene().getWindow()).close();
        }
    }

    /**
     * Crea un diálogo modal con el contenido indicado, aplica el tema activo
     * y lo muestra de forma bloqueante.
     *
     * @param root  Contenido raíz a mostrar en el diálogo.
     * @param title Título de la ventana del diálogo.
     */
    private void showAsDialog(VBox root, String title) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(taskTitleLabel.getScene().getWindow());
        Scene scene = new Scene(root);
        applyThemeToScene(scene);
        dialog.setScene(scene);
        dialog.centerOnScreen();
        System.out.println("centrado");
        dialog.showAndWait();
    }

    /**
     * Aplica el tema activo del {@link com.taskmaster.taskmasterfrontend.util.ThemeManager}
     * a la escena indicada, cargando primero el CSS base y luego el tema seleccionado.
     *
     * @param scene Escena a la que aplicar el tema.
     */
    private void applyThemeToScene(Scene scene) {
        com.taskmaster.taskmasterfrontend.util.ThemeManager tm =
                com.taskmaster.taskmasterfrontend.util.ThemeManager.getInstance();
        // Cargar siempre el CSS base primero
        String baseUrl = getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css") != null
                ? getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css").toExternalForm()
                : null;
        if (baseUrl != null) scene.getStylesheets().add(baseUrl);
        // Luego el tema activo si no es Amatista
        String cssFile = "/com/taskmaster/taskmasterfrontend/themes/"
                + tm.getCssFileNamePublic();
        String themeUrl = getClass().getResource(cssFile) != null
                ? getClass().getResource(cssFile).toExternalForm()
                : null;
        if (themeUrl != null && !themeUrl.equals(baseUrl))
            scene.getStylesheets().add(themeUrl);
        // Fondo del Scene
        scene.setFill(javafx.scene.paint.Color.web(tm.getBgApp()));
    }

    /**
     * Devuelve el color hex asociado a un estado de tarea.
     *
     * @param s Código de estado (p.ej. {@code "IN_PROGRESS"}).
     * @return Color en formato hex.
     */
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

    /**
     * Devuelve el color hex asociado a una prioridad de tarea.
     *
     * @param p Código de prioridad (p.ej. {@code "HIGH"}).
     * @return Color en formato hex.
     */
    private String getPriorityColor(String p) {
        return switch (p) {
            case "URGENT" -> "#e74c3c";
            case "HIGH"   -> "#e67e22";
            case "MEDIUM" -> "#3498db";
            case "LOW"    -> "#95a5a6";
            default       -> "#95a5a6";
        };
    }

    /**
     * Devuelve el estilo CSS inline del badge de categoría.
     *
     * @param c Código de categoría (p.ej. {@code "PERSONAL"}).
     * @return Cadena de estilo CSS con color de fondo y de texto.
     */
    private String getCategoryBadgeStyle(String c) {
        return switch (c) {
            case "PERSONAL" -> "-fx-background-color: #f3e8ff; -fx-text-fill: #6b21a8;";
            case "ESTUDIOS" -> "-fx-background-color: #fef3c7; -fx-text-fill: #92400e;";
            case "TRABAJO"  -> "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af;";
            default         -> "-fx-background-color: #f0f0f5; -fx-text-fill: #666666;";
        };
    }

    /**
     * Traduce un código de estado del backend a su etiqueta localizada.
     *
     * @param status Código de estado (p.ej. {@code "TODO"}).
     * @return Etiqueta localizada correspondiente.
     */
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

    /**
     * Traduce un código de prioridad del backend a su etiqueta localizada.
     *
     * @param priority Código de prioridad (p.ej. {@code "MEDIUM"}).
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
