package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.MenuButtonFactory;
import com.taskmaster.taskmasterfrontend.util.TaskStyleHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Controlador de la vista de detalle de proyecto.
 *
 * <p>Muestra la información del proyecto (nombre, estado, prioridad, categoría
 * y descripción), las estadísticas de sus tareas con barra de progreso, la
 * lista de tareas asociadas y el historial de actividad. Permite editar el
 * proyecto, crear nuevas tareas, editar y eliminar tareas existentes, y abrir
 * el detalle de una tarea concreta.</p>
 *
 * @author Carlos
 */
public class ProjectDetailController {

    private static final Logger log = LoggerFactory.getLogger(ProjectDetailController.class);

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
    private Runnable onProjectUpdated;
    private JsonNode projectData;
    private Consumer<JsonNode> onOpenTaskDetail;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    // -------------------------------------------------------------------------
    // Configuración desde el controlador padre
    // -------------------------------------------------------------------------

    /**
     * Carga los datos del proyecto en la vista e inicializa el historial
     * de actividad del proyecto y sus tareas.
     *
     * @param project nodo JSON con los datos del proyecto
     */
    public void initData(JsonNode project) {
        this.projectData = project;
        loadProjectDetail();
        Long projectId = project.path("id").asLong();
        activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
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
     * Registra el callback que se ejecutará tras actualizar el proyecto.
     *
     * @param callback acción a ejecutar al completar la actualización
     */
    public void setOnProjectUpdated(Runnable callback) {
        this.onProjectUpdated = callback;
    }

    /**
     * Registra el callback que se ejecutará al abrir el detalle de una tarea.
     *
     * @param callback consumidor que recibe el nodo JSON de la tarea seleccionada
     */
    public void setOnOpenTaskDetail(java.util.function.Consumer<JsonNode> callback) {
        this.onOpenTaskDetail = callback;
    }

    // -------------------------------------------------------------------------
    // Carga y renderizado principal
    // -------------------------------------------------------------------------

    /**
     * Rellena los campos de cabecera del proyecto e inicia la carga asíncrona
     * de las tareas asociadas.
     */
    private void loadProjectDetail() {
        long id = projectData.get("id").asLong();
        String name = projectData.get("name").asText();
        String status = projectData.has("status")   && !projectData.get("status").isNull()
                ? projectData.get("status").asText() : "TODO";
        String priority = projectData.has("priority") && !projectData.get("priority").isNull()
                ? projectData.get("priority").asText() : "MEDIUM";
        String category = projectData.has("category") && !projectData.get("category").isNull()
                ? projectData.get("category").asText() : "PERSONAL";
        String desc = projectData.has("description") && !projectData.get("description").isNull()
                ? projectData.get("description").asText() : "";

        projectIdLabel.setText("#" + id);
        projectNameLabel.setText(name);
        descriptionLabel.setText(desc.isEmpty() ? lm.get("common.no.description") : desc);

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

        loadTasks(id);
    }

    /**
     * Obtiene las tareas del proyecto desde el backend y las renderiza.
     *
     * @param projectId identificador del proyecto
     */
    private void loadTasks(Long projectId) {
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance().getApiService()
                        .get("/api/tasks?projectId=" + projectId);
                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.load.tasks")));
            }
        }, "project-detail-load-tasks");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Renderiza la lista de tareas del proyecto, calcula estadísticas
     * y actualiza la barra de progreso.
     *
     * @param tasks array JSON con las tareas del proyecto
     */
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
            } else if (!"CANCELLED".equals(s)) {
                pending++;
                if (task.has("dueDate") && !task.get("dueDate").isNull()) {
                    try {
                        LocalDate due = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                        if (due.isBefore(today)) overdue++;
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

    /**
     * Construye la fila visual de una tarea con checkbox, título, badges
     * y menú de acciones.
     *
     * @param task nodo JSON con los datos de la tarea
     * @return {@link HBox} con el contenido visual de la fila
     */
    private HBox createTaskRow(JsonNode task) {
        String status = task.has("status")   ? task.get("status").asText() : "TODO";
        String title = task.get("title").asText();
        String priority = task.has("priority") ? task.get("priority").asText() : "MEDIUM";
        boolean isDone = "DONE".equals(status);
        long taskId = task.path("id").asLong();

        CheckBox check = new CheckBox();
        check.setSelected(isDone);

        final boolean[] updating = {false};
        check.selectedProperty().addListener((obs, was, is) -> {
            if (updating[0]) return;
            String newStatus = is ? "DONE" : "TODO";
            Thread t = new Thread(() -> {
                try {
                    HttpResponse<String> resp = AppContext.getInstance().getApiService()
                            .patch("/api/tasks/" + taskId + "/status?status=" + newStatus, null);
                    Platform.runLater(() -> {
                        if (resp.statusCode() == 200) {
                            long projectId = projectData.path("id").asLong();
                            loadTasks(projectId);
                            activityLogSectionController.loadForEntity(
                                    "PROJECT", projectId, "TASK");
                        } else {
                            updating[0] = true;
                            check.setSelected(was);
                            updating[0] = false;
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        updating[0] = true;
                        check.setSelected(was);
                        updating[0] = false;
                    });
                }
            }, "project-task-status-change");
            t.setDaemon(true);
            t.start();
        });

        Label titleLabel = new Label(title);
        titleLabel.setOnMouseClicked(e -> openTaskDetail(task));
        titleLabel.setStyle(isDone
                ? "-fx-font-size: 13px; -fx-text-fill: -tm-text-muted; "
                + "-fx-strikethrough: true; -fx-cursor: hand;"
                : "-fx-font-size: 13px; -fx-text-fill: -tm-text-primary; "
                + "-fx-cursor: hand;");

        Label statusLbl = new Label(TaskStyleHelper.translateStatus(status, lm));
        statusLbl.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; "
                + "-fx-background-radius: 10px; -fx-text-fill: white; "
                + "-fx-background-color: " + TaskStyleHelper.getStatusColor(status) + ";");

        Label priBadge = new Label(TaskStyleHelper.translatePriority(priority, lm));
        priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; "
                + "-fx-background-radius: 10px; -fx-text-fill: white; "
                + "-fx-background-color: " + TaskStyleHelper.getPriorityColor(priority) + ";");

        HBox row = new HBox(10, check, titleLabel, statusLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 8 0 8 0;");

        // Badge de tarea vencida
        if (task.has("dueDate") && !task.get("dueDate").isNull() && !isDone) {
            try {
                LocalDate due = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                if (due.isBefore(LocalDate.now())) {
                    Label overdueLabel = new Label(lm.get("date.overdue"));
                    overdueLabel.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; "
                            + "-fx-background-radius: 10px; -fx-text-fill: #991b1b; "
                            + "-fx-background-color: #fee2e2;");
                    row.getChildren().add(overdueLabel);
                }
            } catch (Exception ignored) {}
        }

        row.getChildren().add(priBadge);

        Button menuBtn = MenuButtonFactory.createEditDeleteMenu(
                lm.get("common.menu.edit"),
                lm.get("common.menu.delete"),
                () -> openEditTask(task),
                () -> deleteTask(taskId));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(spacer, menuBtn);

        return row;
    }

    // -------------------------------------------------------------------------
    // Navegación a detalle de tarea
    // -------------------------------------------------------------------------

    /**
     * Abre la vista de detalle de una tarea delegando en el callback del padre,
     * o abriendo un diálogo modal como fallback.
     *
     * @param task   nodo JSON con los datos de la tarea
     */
    private void openTaskDetail(JsonNode task) {
        if (onOpenTaskDetail != null) {
            onOpenTaskDetail.accept(task);
            return;
        }

        // Fallback: diálogo modal
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            TaskDetailController controller = loader.getController();
            controller.initData(task);
            showAsDialog(root, lm.get("project.detail.task.detail"));

            long projectId = projectData.path("id").asLong();
            activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
            loadTasks(projectId);
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("error.open.task.detail"));
        }
    }

    /**
     * Abre el diálogo de edición de una tarea del proyecto.
     *
     * @param task   nodo JSON con los datos actuales
     */
    private void openEditTask(JsonNode task) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(task);
            controller.setOnTaskUpdated(() -> {
                Long projectId = projectData.path("id").asLong();
                loadTasks(projectId);
                activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
            });
            showAsDialog(root, lm.get("common.task.edit"));
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("project.detail.task.editor.error"));
        }
    }

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * la tarea del backend y recarga la lista y el historial.
     *
     * @param taskId Identificador de la tarea a eliminar.
     */
    private void deleteTask(Long taskId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("common.delete.task.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("project.detail.delete.content"));
        confirm.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            Thread thread = new Thread(() -> {
                try {
                    AppContext.getInstance().getApiService().delete("/api/tasks/" + taskId);
                    Platform.runLater(() -> {
                        long projectId = projectData.path("id").asLong();
                        loadTasks(projectId);
                        activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.delete.task")));
                }
            }, "project-detail-delete-task");
            thread.setDaemon(true);
            thread.start();
        });
    }

    // -------------------------------------------------------------------------
    // Acciones FXML
    // -------------------------------------------------------------------------

    /**
     * Abre el diálogo modal de edición del proyecto y, tras guardar,
     * recarga los datos del proyecto desde el backend y notifica al controlador padre.
     */
    @FXML
    private void handleEdit() {
        long projectId = projectData.get("id").asLong();
        String projectName = projectData.get("name").asText();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-project-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            EditProjectController controller = loader.getController();
            controller.initData(projectId, projectName);
            controller.setOnProjectUpdated(() -> {
                Thread thread = new Thread(() -> {
                    try {
                        HttpResponse<String> r = AppContext.getInstance()
                                .getApiService().get("/api/projects/" + projectId);
                        if (r.statusCode() == 200) {
                            JsonNode updated = objectMapper.readTree(r.body());
                            Platform.runLater(() -> initData(updated));
                        }
                    } catch (Exception e) {
                        log.warn("Error al recargar proyecto tras edición: {}", e.getMessage());
                    }
                }, "project-detail-reload-after-edit");
                thread.setDaemon(true);
                thread.start();
                if (onProjectUpdated != null) onProjectUpdated.run();
            });
            showAsDialog(root, LanguageManager.getInstance().get("edit.project.title"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    /**
     * Ejecuta el callback de cierre si está registrado, o cierra
     * la ventana directamente en caso contrario.
     */
    @FXML
    private void handleClose() {
        if (onClose != null) onClose.run();
        else ((Stage) projectNameLabel.getScene().getWindow()).close();
    }

    /**
     * Abre el diálogo modal de creación de nueva tarea asociada al proyecto
     * y recarga la lista y el historial al crearla.
     */
    @FXML
    private void handleNewTask() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            NewTaskController controller = loader.getController();
            long projectId = projectData.path("id").asLong();
            controller.initData(projectId);
            controller.setOnTaskCreated(() -> {
                loadTasks(projectId);
                // Breve pausa para que el backend procese el log de actividad
                // antes de recargarlo en el frontend
                PauseTransition pause = new PauseTransition(Duration.millis(300));
                pause.setOnFinished(e ->
                    activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK"));
                pause.play();
            });
            showAsDialog(root, lm.get("new.task.title"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados de utilidad
    // -------------------------------------------------------------------------

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
        dialog.initOwner(projectNameLabel.getScene().getWindow());
        Scene scene = new Scene(root);
        TaskStyleHelper.applyThemeToScene(scene, this);
        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
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