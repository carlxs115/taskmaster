package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.*;
import javafx.application.Platform;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Controlador de la vista de detalle de tarea.
 *
 * <p>Muestra la información completa de una tarea o subtarea: título, estado,
 * prioridad, categoría, fecha límite, descripción, dependencias (tareas predecesoras),
 * subtareas con barra de progreso, registros de trabajo (worklogs) y el historial
 * de actividad. Permite editar la tarea, gestionar dependencias, crear subtareas,
 * gestionar worklogs y abrir el detalle de una subtarea concreta.</p>
 *
 * @author Carlos
 */
public class TaskDetailController {

    private static final Logger log = LoggerFactory.getLogger(TaskDetailController.class);

    @FXML private Label taskIdLabel;
    @FXML private Label taskTitleLabel;
    @FXML private Label statusBadge;
    @FXML private Label priorityBadge;
    @FXML private Label categoryBadge;
    @FXML private Label dueDateLabel;
    @FXML private Label estimatedDurationLabel;
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

    // -- Dependencias --
    @FXML private VBox dependenciesSection;
    @FXML private VBox dependenciesContainer;
    @FXML private Label emptyDependenciesLabel;
    @FXML private Label dependenciesCountLabel;

    private boolean isSubtask = false;
    private Runnable onClose;
    private JsonNode taskData;
    private Runnable onTaskChanged;
    private Consumer<JsonNode> onOpenSubtaskDetail;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    // -------------------------------------------------------------------------
    // Configuración desde el controlador padre
    // -------------------------------------------------------------------------

    /**
     * Registra el callback que se ejecutará cuando la tarea o alguna de sus
     * subtareas cambie de estado.
     *
     * @param callback acción a ejecutar al detectar un cambio
     */
    public void setOnTaskChanged(Runnable callback) {
        this.onTaskChanged = callback;
    }

    /**
     * Registra el callback que se ejecutará al cerrar la vista de detalle.
     *
     * @param callback acción a ejecutar al cerrar
     */
    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    /**
     * Registra el callback que se ejecutará al abrir el detalle de una subtarea.
     *
     * @param callback consumidor que recibe el nodo JSON de la subtarea seleccionada
     */
    public void setOnOpenSubtaskDetail(Consumer<JsonNode> callback) {
        this.onOpenSubtaskDetail = callback;
    }

    // -------------------------------------------------------------------------
    // Inicialización de datos
    // -------------------------------------------------------------------------

    /**
     * Carga los datos de una tarea en la vista e inicializa el historial
     * de actividad de la tarea y sus subtareas.
     *
     * <p>Realiza un render inicial con los datos en memoria y, a continuación,
     * hace una llamada fresca a la API para garantizar que campos opcionales
     * como {@code estimatedDuration} están actualizados.</p>
     *
     * @param task nodo JSON con los datos de la tarea a mostrar
     */
    public void initData(JsonNode task) {
        this.taskData = task;
        loadTaskDetail();
        Long taskId = task.path("id").asLong();
        activityLogSectionController.loadForEntity("TASK", taskId, "SUBTASK");
        refreshTaskDataFromApi(taskId, false);
    }

    /**
     * Carga los datos de una subtarea en la vista, inicializa su historial
     * de actividad y oculta la sección de subtareas, ya que las subtareas
     * no pueden tener subtareas propias.
     *
     * <p>Realiza un render inicial con los datos en memoria y después una
     * recarga fresca desde la API.</p>
     *
     * @param subtask nodo JSON con los datos de la subtarea a mostrar
     */
    public void initDataAsSubtask(JsonNode subtask) {
        this.taskData = subtask;
        this.isSubtask = true;
        loadTaskDetail();
        Long subtaskId = subtask.path("id").asLong();
        activityLogSectionController.loadForEntity("SUBTASK", subtaskId);
        subtasksSection.setVisible(false);
        subtasksSection.setManaged(false);

        // Las subtareas no tienen sección de dependencias
        dependenciesSection.setVisible(false);
        dependenciesSection.setManaged(false);

        refreshTaskDataFromApi(subtaskId, true);
    }

    /**
     * Recarga los datos de la tarea desde la API en segundo plano y actualiza
     * la vista. Garantiza que campos como {@code estimatedDuration} se muestran
     * aunque el JSON en memoria viniera de una versión cacheada del listado.
     *
     * @param taskId    identificador de la tarea o subtarea
     * @param isSubtask {@code true} si es una subtarea (oculta la sección de subtareas)
     */
    private void refreshTaskDataFromApi(Long taskId, boolean isSubtask) {
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().get("/api/tasks/" + taskId);
                if (r.statusCode() == 200) {
                    JsonNode fresh = objectMapper.readTree(r.body());
                    Platform.runLater(() -> {
                        this.taskData = fresh;
                        loadTaskDetail();
                        if (isSubtask) {
                            subtasksSection.setVisible(false);
                            subtasksSection.setManaged(false);
                            dependenciesSection.setVisible(false);
                            dependenciesSection.setManaged(false);
                        }
                    });
                }
            } catch (Exception ignored) {
                // Si la API no responde usamos los datos en memoria ya renderizados
            }
        }, "task-detail-refresh");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Carga y renderizado principal
    // -------------------------------------------------------------------------

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
        long taskId = taskData.get("id").asLong();

        taskIdLabel.setText("#" + taskId);
        taskTitleLabel.setText(title);
        descriptionLabel.setText(desc.isEmpty() ? lm.get("common.no.description") : desc);

        // Badges de estado, prioridad y categoría
        statusBadge.setText(TaskStyleHelper.translateStatus(status, lm));
        statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; "
                + "-fx-background-radius: 10px; -fx-text-fill: white; "
                + "-fx-background-color: " + TaskStyleHelper.getStatusColor(status) + ";");

        priorityBadge.setText(TaskStyleHelper.translatePriority(priority, lm));
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; "
                + "-fx-background-radius: 10px; -fx-text-fill: white; "
                + "-fx-background-color: " + TaskStyleHelper.getPriorityColor(priority) + ";");

        categoryBadge.setText(category);
        categoryBadge.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; "
                + "-fx-background-radius: 10px; "
                + TaskStyleHelper.getCategoryBadgeStyle(category));

        // Fecha límite - marcamos en rojo si está vencida
        if (taskData.has("dueDate") && !taskData.get("dueDate").isNull()) {
            try {
                LocalDate due = LocalDate.parse(taskData.get("dueDate").asText().substring(0, 10));
                boolean overdue = due.isBefore(LocalDate.now())
                        && !"DONE".equals(status) && !"CANCELLED".equals(status);
                dueDateLabel.setText("📅 " + DateFormatManager.getInstance().format(due));
                if (overdue) {
                    dueDateLabel.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; "
                            + "-fx-background-radius: 10px; -fx-text-fill: #991b1b; "
                            + "-fx-background-color: #fee2e2;");
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

        // Duración estimada — badge junto a estado/prioridad/fecha límite
        if (taskData.has("estimatedDuration") && !taskData.get("estimatedDuration").isNull()) {
            String est = new BigDecimal(taskData.get("estimatedDuration").asText())
                    .stripTrailingZeros().toPlainString();
            FontIcon estIcon = new FontIcon(IconCatalog.UI_ESTIMATED_DURATION);
            estIcon.setIconSize(11);
            estimatedDurationLabel.setGraphic(estIcon);
            estimatedDurationLabel.setGraphicTextGap(5);
            estimatedDurationLabel.setText(
                    MessageFormat.format(lm.get("task.detail.estimated.duration"), est));
            estimatedDurationLabel.setStyle(
                    "-fx-font-size: 11px; -fx-padding: 3 10 3 10; "
                    + "-fx-background-radius: 10px; "
                    + "-fx-text-fill: -tm-text-secondary; "
                    + "-fx-background-color: -tm-bg-app;");
            estimatedDurationLabel.setVisible(true);
            estimatedDurationLabel.setManaged(true);
        } else {
            estimatedDurationLabel.setGraphic(null);
            estimatedDurationLabel.setVisible(false);
            estimatedDurationLabel.setManaged(false);
        }

        loadDependencies(taskId);
        loadSubtasks(taskId);
        loadTotalHours(taskId);
        loadWorkLogs(taskId);
    }

    // -------------------------------------------------------------------------
    // Dependencias
    // -------------------------------------------------------------------------

    /**
     * Obtiene en segundo plano las tareas predecesoras de la tarea indicada
     * y las renderiza en la sección de dependencias.
     *
     * @param taskId identificador de la tarea cuyas dependencias se cargan
     */
    private void loadDependencies(long taskId) {
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks/" + taskId + "/dependencies");
                if (response.statusCode() == 200) {
                    JsonNode deps = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderDependencies(deps, taskId));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"),
                        lm.get("task.detail.dependencies.error.load")));
            }
        }, "task-detail-load-deps");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Renderiza la lista de tareas predecesoras en el contenedor de dependencias.
     * Muestra el contador, el icono de bloqueo/completado y el botón de eliminar
     * para cada dependencia. Si la lista está vacía muestra la etiqueta vacía.
     *
     * @param deps   array JSON con las tareas predecesoras
     * @param taskId identificador de la tarea dependiente (necesario para las acciones de cada fila)
     */
    private void renderDependencies(JsonNode deps, long taskId) {
        dependenciesContainer.getChildren().clear();

        if (!deps.isArray() || deps.isEmpty()) {
            emptyDependenciesLabel.setVisible(true);
            emptyDependenciesLabel.setManaged(true);
            dependenciesCountLabel.setText("");
            return;
        }

        emptyDependenciesLabel.setVisible(false);
        emptyDependenciesLabel.setManaged(false);
        dependenciesCountLabel.setText("(" + deps.size() + ")");

        for (JsonNode dep : deps) {
            dependenciesContainer.getChildren().add(createDependencyRow(dep, taskId));
        }
    }

    /**
     * Construye la fila visual de una tarea predecesora con icono de estado de bloqueo,
     * ID, título, badge de estado y botón para eliminar la dependencia.
     *
     * <p>El icono es un candado naranja si la predecesora no está completada
     * (la tarea actual está bloqueada) o un check verde si ya está completada.</p>
     *
     * @param dep    nodo JSON con los datos de la tarea predecesora
     * @param taskId identificador de la tarea dependiente, usado en la acción de eliminar
     * @return {@link HBox} con el contenido visual de la fila
     */
    private HBox createDependencyRow(JsonNode dep, long taskId) {
        long depId = dep.get("id").asLong();
        String status = dep.get("status").asText();
        String title = dep.get("title").asText();
        boolean blocked = !"DONE".equals(status) && !"CANCELLED".equals(status);

        FontIcon lockIcon = new FontIcon(blocked ? IconCatalog.DEP_BLOCKED : IconCatalog.DEP_UNBLOCKED);
        lockIcon.setIconSize(13);
        lockIcon.setIconColor(javafx.scene.paint.Color.web(blocked ? "#f59e0b" : "#22c55e"));

        Label idLabel = new Label("#" + depId);
        idLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -tm-text-secondary; -fx-min-width: 35px;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("task-title");
        titleLabel.setCursor(javafx.scene.Cursor.HAND);
        titleLabel.setOnMouseClicked(e -> openDependencyTaskDetail(depId, taskId));
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label statusLbl = new Label(TaskStyleHelper.translateStatus(status, lm));
        statusLbl.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; "
                + "-fx-background-radius: 10px; -fx-text-fill: white; "
                + "-fx-background-color: " + TaskStyleHelper.getStatusColor(status) + ";");

        Button removeBtn = new Button();
        FontIcon removeIcon = new FontIcon(IconCatalog.ACTION_CANCEL);
        removeIcon.setIconSize(10);
        removeBtn.setGraphic(removeIcon);
        removeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-cursor: hand;");
        removeBtn.setOnAction(e -> removeDependency(depId, taskId));

        HBox row = new HBox(8, lockIcon, idLabel, titleLabel, statusLbl, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-field-row");
        return row;
    }

    /**
     * Abre en un diálogo modal el detalle de la tarea predecesora indicada.
     *
     * <p>Carga los datos de la tarea desde la API en segundo plano y abre la vista
     * de detalle completa. Al cerrar el diálogo recarga la lista de dependencias de
     * la tarea actual para reflejar posibles cambios de estado.</p>
     *
     * @param depTaskId     identificador de la tarea predecesora a abrir
     * @param currentTaskId identificador de la tarea actual (para refrescar sus dependencias al volver)
     */
    private void openDependencyTaskDetail(long depTaskId, long currentTaskId) {
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> resp = AppContext.getInstance().getApiService()
                        .get("/api/tasks/" + depTaskId);
                if (resp.statusCode() != 200) return;
                JsonNode depTask = objectMapper.readTree(resp.body());
                Platform.runLater(() -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"),
                                LanguageManager.getInstance().getBundle());
                        javafx.scene.Parent root = loader.load();
                        TaskDetailController ctrl = loader.getController();

                        Stage dialog = new Stage();
                        ctrl.initData(depTask);
                        ctrl.setOnClose(() -> {
                            dialog.close();
                            loadDependencies(currentTaskId);
                        });
                        ctrl.setOnTaskChanged(() -> loadDependencies(currentTaskId));

                        dialog.setTitle(depTask.path("title").asText());
                        dialog.initModality(Modality.APPLICATION_MODAL);
                        dialog.initOwner(taskTitleLabel.getScene().getWindow());
                        Scene scene = new Scene(root);
                        TaskStyleHelper.applyThemeToScene(scene, this);
                        dialog.setScene(scene);
                        dialog.setMinWidth(720);
                        dialog.setMinHeight(520);
                        dialog.show();
                    } catch (IOException e) {
                        showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.open.dialog")));
            }
        }, "task-dep-open-detail");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Muestra un diálogo de entrada de texto para que el usuario introduzca el ID
     * de la tarea predecesora que desea añadir como dependencia.
     *
     * <p>Valida que el valor introducido sea numérico y envía la petición al backend.
     * Si el backend rechaza la operación (ciclo, tarea no encontrada, sin permisos),
     * muestra un mensaje de error.</p>
     */
    @FXML
    private void handleAddDependency() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(lm.get("task.detail.dependencies.add.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(lm.get("task.detail.dependencies.add.prompt"));

        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) return;
            try {
                long dependsOnId = Long.parseLong(trimmed);
                long taskId = taskData.get("id").asLong();

                Thread t = new Thread(() -> {
                    try {
                        HttpResponse<String> resp = AppContext.getInstance().getApiService()
                                .postWithAuthNoBody("/api/tasks/" + taskId + "/dependencies/" + dependsOnId);
                        Platform.runLater(() -> {
                            if (resp.statusCode() == 200) {
                                loadDependencies(taskId);
                                activityLogSectionController.loadForEntity("TASK", taskId, "SUBTASK");
                            } else {
                                showAlert(lm.get("error.title"), lm.get("task.detail.dependencies.error.add"));
                            }
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> showAlert(lm.get("error.title"),
                                lm.get("task.detail.dependencies.error.add")));
                    }
                }, "task-detail-add-dep");
                t.setDaemon(true);
                t.start();

            } catch (NumberFormatException e) {
                showAlert(lm.get("error.title"), lm.get("task.detail.dependencies.error.invalid.id"));
            }
        });
    }

    /**
     * Solicita confirmación y elimina la relación de dependencia entre la tarea actual
     * y la tarea predecesora indicada.
     *
     * @param depId  identificador de la tarea predecesora a desvincular
     * @param taskId identificador de la tarea dependiente (la tarea actual)
     */
    private void removeDependency(long depId, long taskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("task.detail.dependencies.remove.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("task.detail.dependencies.remove.content"));
        confirm.showAndWait().ifPresent(r -> {

            if (r != ButtonType.OK) return;

            Thread t = new Thread(() -> {
                try {
                    AppContext.getInstance().getApiService()
                            .delete("/api/tasks/" + taskId + "/dependencies/" + depId);
                    Platform.runLater(() -> {
                        loadDependencies(taskId);
                        activityLogSectionController.loadForEntity("TASK", taskId, "SUBTASK");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(lm.get("error.title"),
                            lm.get("task.detail.dependencies.error.remove")));
                }
            }, "task-detail-remove-dep");
            t.setDaemon(true);
            t.start();
        });
    }

    // -------------------------------------------------------------------------
    // Subtareas
    // -------------------------------------------------------------------------

    /**
     * Obtiene las subtareas de la tarea desde el backend y las renderiza.
     *
     * @param taskId identificador de la tarea padre
     */
    private void loadSubtasks(Long taskId) {
        Thread t = new Thread(() -> {
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
        }, "task-detail-load-subtasks");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Renderiza la lista de subtareas, calcula el progreso y actualiza
     * la barra de progreso y la etiqueta de contador.
     *
     * @param subtasks     array JSON con las subtareas
     * @param parentTaskId identificador de la tarea padre
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

        // Calculamos el progreso de subtareas completadas
        int total = subtasks.size();
        int done  = 0;
        for (JsonNode st : subtasks) {
            if ("DONE".equals(st.has("status") ? st.get("status").asText() : "")) done++;
        }
        subtaskProgressLabel.setText(MessageFormat.format(lm.get("task.detail.subtasks.progress"), done, total));

        progressBarBg.setVisible(true);
        progressBarBg.setManaged(true);
        double pct = (double) done / total;
        progressBarBg.widthProperty().addListener((obs, o, n) ->
                progressBarFill.setPrefWidth(n.doubleValue() * pct));

        for (JsonNode subtask : subtasks) {
            subtaskContainer.getChildren().add(createSubtaskRow(subtask, parentTaskId));
        }
    }

    /**
     * Construye la fila visual de una subtarea con checkbox, título, badges
     * y menú de acciones.
     *
     * @param subtask      nodo JSON con los datos de la subtarea
     * @param parentTaskId identificador de la tarea padre
     * @return {@link HBox} con el contenido visual de la fila
     */
    private HBox createSubtaskRow(JsonNode subtask, Long parentTaskId) {
        String status = subtask.get("status").asText();
        String title = subtask.get("title").asText();
        String priority = subtask.has("priority") ? subtask.get("priority").asText() : "MEDIUM";
        long stId = subtask.get("id").asLong();
        boolean isDone = "DONE".equals(status);

        CheckBox check = new CheckBox();
        check.setSelected(isDone);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add(isDone ? "task-title-done" : "task-title");
        titleLabel.setOnMouseClicked(e -> openSubtaskDetail(stId));
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label statusLbl = new Label(TaskStyleHelper.translateStatus(status, lm));
        statusLbl.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; "
                + "-fx-background-radius: 10px; -fx-text-fill: white; "
                + "-fx-background-color: " + TaskStyleHelper.getStatusColor(status) + ";");

        Label priBadge = new Label(TaskStyleHelper.translatePriority(priority, lm));
        priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; "
                + "-fx-background-radius: 10px; -fx-text-fill: white; "
                + "-fx-background-color: " + TaskStyleHelper.getPriorityColor(priority) + ";");

        Button menuBtn = MenuButtonFactory.createEditDeleteMenu(
                lm.get("common.menu.edit"),
                lm.get("common.menu.delete"),
                () -> openEditSubtask(stId, parentTaskId),
                () -> deleteSubtask(stId, parentTaskId));

        // Array para esquivar la limitación de variables efectivamente finales en lambdas
        final boolean[] updating = {false};
        check.selectedProperty().addListener((obs, was, is) -> {
            if (updating[0]) return;
            String newStatus = is ? "DONE" : "TODO";
            Thread thread = new Thread(() -> {
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
                            activityLogSectionController.loadForEntity(
                                    "TASK", parentTaskId, "SUBTASK");
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
            }, "task-subtask-status-change");
            thread.setDaemon(true);
            thread.start();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, check, titleLabel, statusLbl, priBadge, spacer, menuBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-field-row");
        return row;
    }

    /**
     * Abre la vista de detalle de una subtarea delegando en el callback
     * del controlador padre, o abriendo un diálogo modal como fallback.
     *
     * @param subtaskId identificador de la subtarea
     */
    private void openSubtaskDetail(long subtaskId) {
        try {
            HttpResponse<String> resp = AppContext.getInstance().getApiService().get("/api/tasks/" + subtaskId);
            if (resp.statusCode() != 200) return;
            JsonNode subtask = new ObjectMapper().registerModule(new JavaTimeModule()).readTree(resp.body());

            if (onOpenSubtaskDetail != null) {
                onOpenSubtaskDetail.accept(subtask);
                return;
            }

            // Fallback: diálogo modal si no hay callback registrado
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"),
                    LanguageManager.getInstance().getBundle());
            VBox root = loader.load();
            TaskDetailController controller = loader.getController();
            controller.initDataAsSubtask(subtask);

            Stage dialog = new Stage();
            dialog.setTitle(lm.get("task.detail.subtask.detail"));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(taskTitleLabel.getScene().getWindow());

            Scene scene = new Scene(root);
            TaskStyleHelper.applyThemeToScene(scene, this);
            dialog.setScene(scene);
            dialog.showAndWait();

            loadSubtasks(taskData.get("id").asLong());

        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("error.open.subtask.detail"));
        }
    }

    /**
     * Abre el diálogo de edición de una subtarea y recarga al guardar.
     *
     * @param subtaskId    identificador de la subtarea
     * @param parentTaskId identificador de la tarea padre
     */
    private void openEditSubtask(Long subtaskId, Long parentTaskId) {
        try {
            HttpResponse<String> resp = AppContext.getInstance().getApiService().get("/api/tasks/" + subtaskId);
            if (resp.statusCode() != 200) return;
            JsonNode subtask = new ObjectMapper().registerModule(new JavaTimeModule()).readTree(resp.body());

            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle());
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(subtask);
            controller.setDialogTitle(lm.get("task.detail.subtask.edit"));
            controller.setOnTaskUpdated(() -> {
                loadSubtasks(parentTaskId);
                activityLogSectionController.loadForEntity(
                        "TASK", taskData.get("id").asLong(), "SUBTASK");
            });
            showAsDialog(root, lm.get("task.detail.subtask.edit"));

        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("task.detail.subtask.error.edit"));
        }
    }

    /**
     * Muestra confirmación y elimina permanentemente una subtarea.
     *
     * @param subtaskId    identificador de la subtarea
     * @param parentTaskId identificador de la tarea padre
     */
    private void deleteSubtask(long subtaskId, long parentTaskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("task.detail.subtask.delete.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("task.detail.subtask.delete.content"));
        confirm.showAndWait().ifPresent(r -> {

            if (r != ButtonType.OK) return;

            Thread t = new Thread(() -> {
                try {
                    AppContext.getInstance().getApiService().delete("/api/tasks/" + subtaskId);
                    Platform.runLater(() -> {
                        loadSubtasks(parentTaskId);
                        activityLogSectionController.loadForEntity(
                                "TASK", parentTaskId, "SUBTASK");
                        if (onTaskChanged != null) onTaskChanged.run();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(lm.get("error.title"),
                            lm.get("task.detail.subtask.error.delete")));
                }
            }, "task-detail-delete-subtask");
            t.setDaemon(true);
            t.start();
        });
    }

    // -------------------------------------------------------------------------
    // Worklogs
    // -------------------------------------------------------------------------

    /**
     * Obtiene los registros de trabajo de la tarea desde el backend.
     *
     * @param taskId identificador de la tarea
     */
    private void loadWorkLogs(long taskId) {
        Thread thread = new Thread(() -> {
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
        }, "task-detail-load-worklogs");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Renderiza la lista de worklogs con fecha, horas, tipo y nota.
     *
     * @param logs array JSON con los registros de trabajo
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
            long logId = log.path("id").asLong();
            String date = log.path("date").asText().substring(0, 10);
            String activityType = translateActivityType(log.path("activityType").asText());
            String hours = log.path("hours").asText();
            String note = log.path("note").isNull() ? "" : log.path("note").asText();

            Label dateLbl = new Label(date);
            dateLbl.getStyleClass().add("profile-field-label");
            dateLbl.setStyle("-fx-min-width: 85px;");

            Label typeLbl = new Label(activityType);
            typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; "
                    + "-fx-background-radius: 10px; "
                    + "-fx-background-color: -tm-bg-active; "
                    + "-fx-text-fill: -tm-accent-light;");

            Label hoursLbl = new Label(hours + "h");
            hoursLbl.getStyleClass().add("profile-field-value");
            hoursLbl.setStyle("-fx-font-weight: bold; " + "-fx-min-width: 40px; -fx-alignment: CENTER_RIGHT;");

            Label noteLbl = new Label(note);
            noteLbl.getStyleClass().add("profile-field-value");
            noteLbl.setWrapText(true);

            long   taskId  = taskData.get("id").asLong();
            Button menuBtn = MenuButtonFactory.createEditDeleteMenu(
                    lm.get("common.menu.edit"),
                    lm.get("common.menu.delete"),
                    () -> openEditWorkLog(logId, log, taskId),
                    () -> deleteWorkLog(logId, taskId));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, dateLbl, hoursLbl, typeLbl, noteLbl, spacer, menuBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("profile-field-row");
            workLogContainer.getChildren().add(row);
        }
    }

    /**
     * Obtiene el total de horas registradas para la tarea y actualiza la etiqueta.
     *
     * @param taskId identificador de la tarea
     */
    private void loadTotalHours(long taskId) {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance().getApiService()
                        .get("/api/worklogs/task/" + taskId + "/total");
                if (response.statusCode() == 200) {
                    String total = response.body();
                    Platform.runLater(() -> {
                        if (taskData.has("estimatedDuration") && !taskData.get("estimatedDuration").isNull()) {
                            String est = new BigDecimal(taskData.get("estimatedDuration").asText())
                                    .stripTrailingZeros().toPlainString();
                            totalHoursLabel.setText(MessageFormat.format(
                                    lm.get("task.detail.worklog.total.with.estimate"), total, est));
                        } else {
                            totalHoursLabel.setText(MessageFormat.format(
                                    lm.get("task.detail.worklog.total"), total));
                        }
                    });
                }
            } catch (Exception e) {
                // No es crítico si falla el total de horas
                log.warn("No se pudo cargar el total de horas para tarea {}", taskId);
            }
        }, "task-detail-load-hours");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Abre el diálogo de edición de un worklog existente.
     *
     * @param logId  identificador del worklog
     * @param log    nodo JSON con los datos actuales del worklog
     * @param taskId identificador de la tarea
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
     * Muestra confirmación y elimina un worklog.
     *
     * @param logId  identificador del worklog
     * @param taskId identificador de la tarea
     */
    private void deleteWorkLog(Long logId, Long taskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("task.detail.worklog.delete.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("task.detail.worklog.delete.content"));
        confirm.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            Thread thread = new Thread(() -> {
                try {
                    AppContext.getInstance().getApiService().delete("/api/worklogs/" + logId);
                    Platform.runLater(() -> {
                        loadWorkLogs(taskId);
                        loadTotalHours(taskId);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(lm.get("error.title"),
                            lm.get("task.detail.worklog.error.delete")));
                }
            }, "task-detail-delete-worklog");
            thread.setDaemon(true);
            thread.start();
        });
    }

    // -------------------------------------------------------------------------
    // Acciones FXML
    // -------------------------------------------------------------------------

    /**
     * Abre el diálogo para añadir un nuevo worklog a la tarea.
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
            long taskId = taskData.get("id").asLong();
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
     * Abre el diálogo de creación de nueva subtarea.
     */
    @FXML
    private void handleNewSubtask() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/taskmaster/taskmasterfrontend/new-subtask-dialog.fxml"),
                    LanguageManager.getInstance().getBundle());
            VBox root = loader.load();
            NewSubtaskController controller = loader.getController();
            long parentId = taskData.get("id").asLong();
            Long projectId = taskData.has("projectId") && !taskData.get("projectId").isNull()
                    ? taskData.get("projectId").asLong() : null;
            String category = taskData.has("category") ? taskData.get("category").asText() : "PERSONAL";
            controller.initData(parentId, projectId, category);
            controller.setOnSubtaskCreated(() -> {
                loadSubtasks(parentId);
                activityLogSectionController.loadForEntity("TASK", parentId, "SUBTASK");
                if (onTaskChanged != null) onTaskChanged.run();
            });
            showAsDialog(root, lm.get("new.subtask.title"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    /**
     * Abre el diálogo de edición de la tarea o subtarea actual y recarga
     * los datos desde el backend al guardar.
     */
    @FXML
    private void handleEdit() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle());
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(taskData);
            controller.setDialogTitle(isSubtask ? lm.get("task.detail.subtask.edit")
                    : lm.get("common.menu.edit"));
            controller.setOnTaskUpdated(() -> {
                long taskId = taskData.get("id").asLong();
                Thread thread = new Thread(() -> {
                    try {
                        HttpResponse<String> resp = AppContext.getInstance()
                                .getApiService().get("/api/tasks/" + taskId);
                        if (resp.statusCode() == 200) {
                            taskData = objectMapper.readTree(resp.body());
                            Platform.runLater(() -> {
                                loadTaskDetail();
                                if (isSubtask) {
                                    activityLogSectionController.loadForEntity("SUBTASK", taskId);
                                } else {
                                    activityLogSectionController
                                            .loadForEntity("TASK", taskId, "SUBTASK");
                                }
                                if (onTaskChanged != null) onTaskChanged.run();
                            });
                        }
                    } catch (Exception ex) {
                        log.error("Error al recargar la tarea tras edición: {}", ex.getMessage());
                    }
                }, "task-detail-reload-after-edit");
                thread.setDaemon(true);
                thread.start();
            });
            showAsDialog(root, isSubtask ? lm.get("task.detail.subtask.edit") : lm.get("common.menu.edit"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    /**
     * Cierra la vista de detalle ejecutando el callback si está registrado.
     */
    @FXML
    private void handleClose() {
        closeDialog();
    }

    // -------------------------------------------------------------------------
    // Métodos privados de utilidad
    // -------------------------------------------------------------------------

    /**
     * Ejecuta el callback de cierre o cierra la ventana directamente.
     */
    private void closeDialog() {
        if (onClose != null) onClose.run();
        else ((Stage) taskTitleLabel.getScene().getWindow()).close();
    }

    /**
     * Crea un diálogo modal con el contenido indicado, aplica el tema activo
     * y lo muestra de forma bloqueante.
     *
     * @param root contenido raíz del diálogo
     * @param title título de la ventana
     */
    private void showAsDialog(VBox root, String title) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(taskTitleLabel.getScene().getWindow());
        Scene scene = new Scene(root);
        TaskStyleHelper.applyThemeToScene(scene, this);
        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    /**
     * Traduce un código de tipo de actividad de worklog a su etiqueta localizada.
     *
     * @param type código del tipo (p.ej. {@code "DEVELOPMENT"})
     * @return etiqueta localizada, o el propio código si no hay traducción
     */
    private String translateActivityType(String type) {
        String key = "activity." + type;
        String val = lm.get(key);
        return val.startsWith("?") ? type : val;
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