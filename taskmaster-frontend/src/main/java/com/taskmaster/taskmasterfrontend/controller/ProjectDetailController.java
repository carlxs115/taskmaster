package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
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

/**
 * Controlador de la vista de detalle de proyecto.
 *
 * <p>Muestra la información del proyecto (nombre, estado, prioridad, categoría
 * y descripción), las estadísticas de sus tareas (total, pendientes, completadas
 * y vencidas) con una barra de progreso, la lista de tareas asociadas y el
 * historial de actividad del proyecto y sus tareas. Permite editar el proyecto,
 * crear nuevas tareas, editar y eliminar tareas existentes, y abrir el detalle
 * de una tarea concreta.</p>
 *
 * @author Carlos
 */
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
    private Runnable onProjectUpdated;
    private JsonNode projectData;
    private java.util.function.Consumer<JsonNode> onOpenTaskDetail;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Carga los datos del proyecto en la vista e inicializa el historial
     * de actividad del proyecto y sus tareas.
     *
     * @param project Nodo JSON con los datos del proyecto a mostrar.
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
     * @param callback Acción a ejecutar al cerrar.
     */
    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    /**
     * Registra el callback que se ejecutará tras actualizar el proyecto.
     *
     * @param callback Acción a ejecutar al completar la actualización.
     */
    public void setOnProjectUpdated(Runnable callback) {
        this.onProjectUpdated = callback;
    }

    /**
     * Registra el callback que se ejecutará al abrir el detalle de una tarea,
     * permitiendo que el controlador padre gestione la navegación.
     *
     * @param callback Consumidor que recibe el nodo JSON de la tarea seleccionada.
     */
    public void setOnOpenTaskDetail(java.util.function.Consumer<JsonNode> callback) {
        this.onOpenTaskDetail = callback;
    }

    /**
     * Rellena los campos de cabecera del proyecto con los datos del nodo JSON
     * e inicia la carga asíncrona de las tareas asociadas.
     */
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

    /**
     * Obtiene las tareas del proyecto desde el backend y las renderiza en la vista.
     *
     * @param projectId Identificador del proyecto cuyas tareas se cargan.
     */
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
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.load.tasks")));
            }
        }).start();
    }

    /**
     * Renderiza la lista de tareas del proyecto, calcula las estadísticas
     * (total, pendientes, completadas, vencidas) y actualiza la barra de progreso.
     *
     * @param tasks Array JSON con las tareas del proyecto.
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

    /**
     * Construye la fila visual de una tarea con su indicador de estado,
     * título, badge de prioridad, indicador de vencimiento y menú de acciones.
     *
     * @param task Nodo JSON con los datos de la tarea.
     * @return {@link HBox} con el contenido visual de la fila.
     */
    private HBox createTaskRow(JsonNode task) {
        String status   = task.has("status")   ? task.get("status").asText()   : "TODO";
        String title    = task.get("title").asText();
        String priority = task.has("priority") ? task.get("priority").asText() : "MEDIUM";
        boolean isDone  = "DONE".equals(status);
        Long taskId     = task.path("id").asLong();

        CheckBox check = new CheckBox();
        check.setSelected(isDone);

        final boolean[] updating = {false};
        check.selectedProperty().addListener((obs, was, is) -> {
            if (updating[0]) return;
            String newStatus = is ? "DONE" : "TODO";
            new Thread(() -> {
                try {
                    HttpResponse<String> resp = AppContext.getInstance().getApiService()
                            .patch("/api/tasks/" + taskId + "/status?status=" + newStatus, null);
                    Platform.runLater(() -> {
                        if (resp.statusCode() == 200) {
                            Long projectId = projectData.path("id").asLong();
                            loadTasks(projectId);
                            activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
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
            }).start();
        });

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 8 0 8 0;");

        Label titleLabel = new Label(title);
        titleLabel.setOnMouseClicked(e -> openTaskDetail(task, taskId));
        titleLabel.setStyle(isDone
                ? "-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true; -fx-cursor: hand;"
                : "-fx-font-size: 13px; -fx-text-fill: #1e1e2e; -fx-cursor: hand;");

        // Badge fecha vencida
        boolean isOverdue = false;
        if (task.has("dueDate") && !task.get("dueDate").isNull() && !isDone) {
            try {
                LocalDate due = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                isOverdue = due.isBefore(LocalDate.now());
            } catch (Exception ignored) {}
        }

        Label statusBadge = new Label(translateStatus(status));
        statusBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getStatusColor(status) + ";");

        Label priBadge = new Label(translatePriority(priority));
        priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        row.getChildren().addAll(check, titleLabel, statusBadge);

        if (isOverdue) {
            Label overdueLabel = new Label(lm.get("date.overdue"));
            overdueLabel.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                    "-fx-background-radius: 10px; -fx-text-fill: #991b1b; " +
                    "-fx-background-color: #fee2e2;");
            row.getChildren().add(overdueLabel);
        }

        row.getChildren().add(priBadge);

        Button menuBtn = MenuButtonFactory.createEditDeleteMenu(
                lm.get("common.menu.edit"),
                lm.get("common.menu.delete"),
                () -> openEditTask(task, taskId),
                () -> deleteTask(taskId)
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);
        row.getChildren().add(menuBtn);
        return row;
    }

    /**
     * Abre la vista de detalle de una tarea. Si hay un callback registrado
     * lo usa para delegar la navegación; si no, abre un diálogo modal.
     *
     * @param task   Nodo JSON con los datos de la tarea.
     * @param taskId Identificador de la tarea.
     */
    private void openTaskDetail(JsonNode task, Long taskId) {
        if (onOpenTaskDetail != null) {
            onOpenTaskDetail.accept(task);
            return;
        }
        // fallback
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            TaskDetailController controller = loader.getController();
            controller.initData(task);
            showAsDialog(root, lm.get("project.detail.task.detail"));
            Long projectId = projectData.path("id").asLong();
            activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
            loadTasks(projectId);
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("error.open.task.detail"));
        }
    }

    /**
     * Abre el diálogo modal de edición de una tarea y recarga la lista
     * y el historial al guardar.
     *
     * @param task   Nodo JSON con los datos actuales de la tarea.
     * @param taskId Identificador de la tarea a editar.
     */
    private void openEditTask(JsonNode task, Long taskId) {
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

    /**
     * Abre el diálogo modal de edición del proyecto y, tras guardar,
     * recarga los datos del proyecto desde el backend y notifica al controlador padre.
     */
    @FXML
    private void handleEdit() {
        Long projectId   = projectData.get("id").asLong();
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
                new Thread(() -> {
                    try {
                        HttpResponse<String> r = AppContext.getInstance()
                                .getApiService().get("/api/projects/" + projectId);
                        if (r.statusCode() == 200) {
                            JsonNode updated = objectMapper.readTree(r.body());
                            Platform.runLater(() -> initData(updated));
                        }
                    } catch (Exception ignored) {}
                }).start();
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
        if (onClose != null) {
            onClose.run();
        } else {
            ((Stage) projectNameLabel.getScene().getWindow()).close();
        }
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
            Long projectId = projectData.path("id").asLong();
            controller.initData(projectId);
            controller.setOnTaskCreated(() -> {
                loadTasks(projectId);
                activityLogSectionController.loadForEntity("PROJECT", projectId, "TASK");
            });
            showAsDialog(root, lm.get("new.task.title"));
        } catch (IOException e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
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
        dialog.initOwner(projectNameLabel.getScene().getWindow());
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
     * Devuelve el color hex asociado a un estado de proyecto o tarea.
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
     * Devuelve el color hex asociado a una prioridad de proyecto o tarea.
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
