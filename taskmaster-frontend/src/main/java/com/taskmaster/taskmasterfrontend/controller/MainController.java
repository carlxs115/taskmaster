package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * MAINCONTROLLER
 *
 * Controlador de la pantalla principal.
 * Gestiona la lista de proyectos en el sidebar y las tareas en el panel derecho.
 */
public class MainController {

    @FXML private Label usernameLabel;
    @FXML private VBox projectListContainer;
    @FXML private Label projectTitleLabel;
    @FXML private VBox taskContainer;
    @FXML private Label emptyLabel;
    @FXML private Button newTaskButton;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> priorityFilter;
    @FXML private VBox projectCardsContainer;
    @FXML private Label emptyProjectsLabel;
    @FXML private ComboBox<String> projectStatusFilter;
    @FXML private ComboBox<String> projectPriorityFilter;

    // ID del proyecto seleccionado actualmente
    private Long selectedProjectId;

    // ObjectMapper para parsear JSON
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final List<Long> projectIds = new ArrayList<>();

    private String selectedCategory;

    private javafx.scene.Node originalRightPanel;

    private TrashController trashController;

    @FXML
    public void initialize() {
        newTaskButton.setDisable(false);

        // Mostramos el nombre del usuario autenticado
        usernameLabel.setText(AppContext.getInstance().getCurrentUsername());

        statusFilter.setItems(FXCollections.observableArrayList(
                "Todos", "TODO", "IN_PROGRESS", "DONE", "CANCELLED"
        ));
        statusFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) handleStatusFilter();
                }
        );
        priorityFilter.setItems(FXCollections.observableArrayList(
                "Todas", "LOW", "MEDIUM", "HIGH", "URGENT"
        ));
        priorityFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) handlePriorityFilter();
                }
        );
        projectStatusFilter.setItems(FXCollections.observableArrayList(
                "Todos", "TODO", "IN_PROGRESS", "DONE", "CANCELLED"
        ));
        projectPriorityFilter.setItems(FXCollections.observableArrayList(
                "Todas", "LOW", "MEDIUM", "HIGH", "URGENT"
        ));

        loadProjects();
        newTaskButton.setDisable(false);
        loadHome();

        Platform.runLater(() -> {
            javafx.scene.layout.BorderPane root =
                    (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
            HBox centerHBox = (HBox) root.getCenter();
            originalRightPanel = centerHBox.getChildren().get(1);
        });
    }

    /**
     * Carga los proyectos del usuario desde el backend.
     */
    public void loadProjects() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/projects");

                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());

                    List<String> names = new ArrayList<>();
                    List<Long> ids = new ArrayList<>();
                    List<String> statuses = new ArrayList<>();
                    List<String> priorities = new ArrayList<>();

                    for (JsonNode project : projects) {
                        names.add(project.get("name").asText());
                        ids.add(project.get("id").asLong());
                        statuses.add(project.has("status") && !project.get("status").isNull()
                                ? project.get("status").asText() : "TODO");
                        priorities.add(project.has("priority") && !project.get("priority").isNull()
                                ? project.get("priority").asText() : "MEDIUM");
                    }

                    Platform.runLater(() -> {
                        projectIds.clear();
                        projectIds.addAll(ids);

                        // Sidebar
                        projectListContainer.getChildren().clear();
                        for (int i = 0; i < names.size(); i++) {
                            final Long projectId = ids.get(i);
                            final String projectName = names.get(i);

                            HBox projectRow = new HBox();
                            projectRow.setAlignment(Pos.CENTER_LEFT);
                            projectRow.setSpacing(4);

                            Button btn = new Button("📁 " + projectName);
                            btn.setMaxWidth(Double.MAX_VALUE);
                            HBox.setHgrow(btn, Priority.ALWAYS);
                            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                                    "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                                    "-fx-padding: 8 4 8 16;");
                            btn.setOnAction(e -> {
                                selectedProjectId = projectId;
                                selectedCategory = null;
                                projectTitleLabel.setText(projectName);
                                newTaskButton.setDisable(false);
                                loadTasksForProject(projectId);
                            });

                            Button menuBtn = new Button("⋯");
                            menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                                    "-fx-cursor: hand; -fx-font-size: 16px; -fx-padding: 4 8 4 4;");
                            menuBtn.setOnAction(e -> {
                                ContextMenu contextMenu = new ContextMenu();
                                MenuItem editItem = new MenuItem("✏ Editar");
                                editItem.setOnAction(ev -> handleEditProject(projectId, projectName));
                                MenuItem deleteItem = new MenuItem("🗑 Eliminar");
                                deleteItem.setOnAction(ev -> handleDeleteProject(projectId, projectName));
                                contextMenu.getItems().addAll(editItem, deleteItem);
                                contextMenu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
                            });

                            projectRow.setOnMouseEntered(e -> {
                                menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                                        "-fx-cursor: hand; -fx-font-size: 16px; -fx-padding: 4 8 4 4;");
                                btn.setStyle("-fx-background-color: #3d3d3d; -fx-text-fill: white; " +
                                        "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                                        "-fx-padding: 8 4 8 16;");
                            });
                            projectRow.setOnMouseExited(e -> {
                                menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                                        "-fx-cursor: hand; -fx-font-size: 16px; -fx-padding: 4 8 4 4;");
                                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                                        "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                                        "-fx-padding: 8 4 8 16;");
                            });

                            projectRow.getChildren().addAll(btn, menuBtn);
                            projectListContainer.getChildren().add(projectRow);
                        }

                        // Panel central de proyectos
                        renderProjectCards(names, ids, statuses, priorities);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudieron cargar los proyectos"));
            }
        }).start();
    }

    private void renderHome(JsonNode home) {
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);
        emptyLabel.setVisible(false);

        boolean hasContent = false;

        // PROYECTOS
        JsonNode projects = home.get("projects");
        if (projects != null && projects.isArray() && !projects.isEmpty()) {
            hasContent = true;
            Label projectsLabel = new Label("📁 PROYECTOS");
            projectsLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #888888; -fx-padding: 8 0 8 0;");
            taskContainer.getChildren().add(projectsLabel);

            for (JsonNode project : projects) {
                Label projectName = new Label("📁 " + project.get("name").asText());
                projectName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; " +
                        "-fx-text-fill: #2d2d2d; -fx-padding: 8 0 4 0;");
                taskContainer.getChildren().add(projectName);

                JsonNode tasks = project.get("tasks");
                if (tasks != null && tasks.isArray() && !tasks.isEmpty()) {
                    for (JsonNode task : tasks) {
                        taskContainer.getChildren().add(createTaskCard(task));
                    }
                } else {
                    Label noTasks = new Label("  Sin tareas");
                    noTasks.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
                    taskContainer.getChildren().add(noTasks);
                }
            }
        }

        // TAREAS SUELTAS POR CATEGORÍA
        hasContent |= renderHomeCategorySection("👤 Personal", home.get("personalTasks"));
        hasContent |= renderHomeCategorySection("📚 Estudios", home.get("estudiosTasks"));
        hasContent |= renderHomeCategorySection("💼 Trabajo", home.get("trabajoTasks"));

        if (!hasContent) {
            emptyLabel.setText("No tienes tareas activas actualmente");
            emptyLabel.setVisible(true);
        }
    }

    private boolean renderHomeCategorySection(String title, JsonNode tasks) {
    if (tasks == null || !tasks.isArray() || tasks.isEmpty()) return false;

    Label categoryLabel = new Label(title);
    categoryLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; " +
            "-fx-text-fill: #888888; -fx-padding: 12 0 8 0;");
    taskContainer.getChildren().add(categoryLabel);

    for (JsonNode task : tasks) {
        taskContainer.getChildren().add(createTaskCard(task));
    }
    return true;
}

    /**
     * Renderiza las tareas en el panel derecho.
     */
    private void renderTasks(JsonNode tasks) {
        // Limpiamos el contenedor manteniendo el label vacío
        taskContainer.getChildren().clear();
        taskContainer.layout();
        taskContainer.getChildren().add(emptyLabel);

        if (!tasks.isArray() || tasks.isEmpty()) {
            emptyLabel.setText("No hay tareas aquí actualmente");
            emptyLabel.setVisible(true);
            return;
        }

        emptyLabel.setVisible(false);

        for (JsonNode task : tasks) {
            taskContainer.getChildren().add(createTaskCard(task));
        }

        taskContainer.layout();
    }

    private void renderProjectCards(List<String> names, List<Long> ids,
                                    List<String> statuses, List<String> priorities) {
        projectCardsContainer.getChildren().clear();
        projectCardsContainer.getChildren().add(emptyProjectsLabel);

        if (names.isEmpty()) {
            emptyProjectsLabel.setVisible(true);
            return;
        }

        emptyProjectsLabel.setVisible(false);

        for (int i = 0; i < names.size(); i++) {
            final Long projectId = ids.get(i);
            final String projectName = names.get(i);
            final String status = statuses.get(i);
            final String priority = priorities.get(i);

            HBox card = new HBox();
            card.setSpacing(10);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 16; " +
                    "-fx-background-radius: 6px; -fx-border-color: #e0e0e0; " +
                    "-fx-border-radius: 6px; -fx-cursor: hand;");
            card.getProperties().put("status", status);
            card.getProperties().put("priority", priority);

            Label nameLabel = new Label("📁 " + projectName);
            nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2d2d2d;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Label statusBadge = new Label(status);
            statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                    "-fx-background-radius: 10px; -fx-text-fill: white; " +
                    "-fx-background-color: " + getStatusColor(status) + ";");

            Label priorityBadge = new Label(priority);
            priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                    "-fx-background-radius: 10px; -fx-text-fill: white; " +
                    "-fx-background-color: " + getPriorityColor(priority) + ";");

            card.setOnMouseClicked(e -> {
                selectedProjectId = projectId;
                selectedCategory = null;
                projectTitleLabel.setText(projectName);
                newTaskButton.setDisable(false);
                loadTasksForProject(projectId);
            });

            card.getChildren().addAll(nameLabel, statusBadge, priorityBadge);
            projectCardsContainer.getChildren().add(card);
        }
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "TODO"        -> "#95a5a6";
            case "IN_PROGRESS" -> "#3498db";
            case "DONE"        -> "#2ecc71";
            case "CANCELLED"   -> "#e74c3c";
            default            -> "#95a5a6";
        };
    }

    /**
     * Crea una tarjeta visual para una tarea.
     */
    private HBox createTaskCard(JsonNode task) {
        HBox card = new HBox();
        card.setSpacing(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 16; " +
                "-fx-background-radius: 6px; -fx-border-color: #e0e0e0; " +
                "-fx-border-radius: 6px;");

        String status = task.get("status").asText();
        String title = task.get("title").asText();
        String priority = task.get("priority").asText();
        Long taskId = task.get("id").asLong();

        // Guardamos status y priority para filtrar en cliente
        card.getProperties().put("status", status);
        card.getProperties().put("priority", priority);

        // Checkbox de completado
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(status.equals("DONE"));

        // Título de la tarea
        Label titleLabel = new Label(title);
        updateTitleStyle(titleLabel, status.equals("DONE"));
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Badge de prioridad
        Label priorityBadge = new Label(priority);
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        // Bandera para evitar que el listener se dispare al actualizar el estilo
        final boolean[] updating = {false};

        /**
         * Listener del checkbox.
         * Cuando el usuario lo marca/desmarca llamamos al backend
         * para cambiar el estado de la tarea.
         * Si el backend rechaza el cambio (ej: subtareas pendientes)
         * revertimos el checkbox y mostramos el error.
         */
        checkBox.selectedProperty().addListener((javafx.beans.value.ObservableValue<? extends Boolean> obs, Boolean wasSelected, Boolean isSelected) -> {
            if (updating[0]) return; // ignoramos si ya estamos actualizando

            final boolean wasSelectedFinal = wasSelected;
            final boolean isSelectedFinal = isSelected;
            String newStatus = isSelectedFinal ? "DONE" : "TODO";

            new Thread(() -> {
                try {
                    HttpResponse<String> response = AppContext.getInstance()
                            .getApiService()
                            .patch("/api/tasks/" + taskId + "/status?status=" + newStatus, null);

                    Platform.runLater(() -> {
                        if (response.statusCode() == 200) {
                            updating[0] = true;
                            // Actualizamos el estilo del título
                            updateTitleStyle(titleLabel, isSelectedFinal);
                            updating[0] = false;
                        } else {
                            updating[0] = true;
                            // Revertimos el checkbox si el backend rechazó el cambio
                            checkBox.setSelected(wasSelectedFinal);
                            updating[0] = false;

                            try {
                                JsonNode error = objectMapper.readTree(response.body());
                                String msg = error.has("error")
                                        ? error.get("error").asText()
                                        : "No se pudo cambiar el estado";
                                showAlert("Error", msg);
                            } catch (Exception e) {
                                showAlert("Error", "No se pudo cambiar el estado");
                            }
                        }
                    });
                } catch (Exception e) {
                    checkBox.setSelected(wasSelectedFinal);
                    showAlert("Error", "Error de conexión con el servidor");
                }
            }).start();
        });

        // Botón editar
        Button editBtn = new Button("✏️");
        editBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14px;");
        editBtn.setOnAction(e -> handleEditTask(taskId, task));

        // Botón eliminar
        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14px;");
        deleteBtn.setOnAction(e -> handleDeleteTask(taskId));

        card.getChildren().addAll(checkBox, titleLabel, priorityBadge, editBtn, deleteBtn);
        return card;
    }

    /**
     * Actualiza el estilo del título según si la tarea está completada o no.
     */
    private void updateTitleStyle(Label titleLabel, boolean done) {
        if (done) {
            titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaaaaa; " +
                    "-fx-strikethrough: true;");
        } else {
            titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2d2d2d;");
        }
    }

    /**
     * Devuelve el color del badge según la prioridad.
     */
    private String getPriorityColor(String priority) {
        return switch (priority) {
            case "URGENT"   -> "#e74c3c";
            case "HIGH"     -> "#e67e22";
            case "MEDIUM"   -> "#3498db";
            case "LOW"      -> "#95a5a6";
            default         -> "#95a5a6";
        };
    }

    @FXML
    private void handleNewProject() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-project-dialog.fxml")
            );
            VBox root = loader.load();
            NewProjectController controller = loader.getController();

            // Cuando se cree el proyecto recargamos la lista
            controller.setOnProjectCreated(() -> {
                loadProjects();
                reloadTasks();
            });

            Stage dialog = new Stage();
            dialog.setTitle("Nuevo proyecto");
            dialog.setScene(new Scene(root, 400, 480));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    @FXML
    private void handleNewTask() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-task-dialog.fxml")
            );
            VBox root = loader.load();
            NewTaskController controller = loader.getController();

            controller.initData(selectedProjectId);
            controller.setOnTaskCreated(this::reloadTasks);

            Stage dialog = new Stage();
            dialog.setTitle("Nueva tarea");
            dialog.setScene(new Scene(root, 600, 420));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    @FXML
    private void handleCategoryPersonal() {
        loadTasksByCategory("PERSONAL", "👤 Personal");
    }

    @FXML
    private void handleCategoryEstudios() {
        loadTasksByCategory("ESTUDIOS", "📚 Estudios");
    }

    @FXML
    private void handleCategoryTrabajo() {
        loadTasksByCategory("TRABAJO", "💼 Trabajo");
    }

    @FXML
    private void handleGoHome() {
        selectedProjectId = null;
        selectedCategory = null;
        projectTitleLabel.setText("Proyectos y tareas");
        newTaskButton.setDisable(false);

        javafx.scene.layout.BorderPane root = (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
        HBox centerHBox = (HBox) root.getCenter();

        if (originalRightPanel != null && centerHBox.getChildren().get(1) != originalRightPanel) {
            centerHBox.getChildren().set(1, originalRightPanel);
        }

        loadHome();
    }

    private void handleEditProject(Long projectId, String projectName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-project-dialog.fxml")
            );

            VBox root = loader.load();
            EditProjectController controller = loader.getController();
            controller.initData(projectId, projectName);
            controller.setOnProjectUpdated(this::loadProjects);

            Stage dialog = new Stage();
            dialog.setTitle("Editar tarea");
            dialog.setScene(new Scene(root, 500, 480));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    private void handleDeleteProject(Long projectId, String projectName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar proyecto");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Seguro que quieres eliminar \"" + projectName + "\"? " +
                "El proyecto y todas sus tareas irán a la papelera.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> httpResponse = AppContext.getInstance()
                                .getApiService()
                                .delete("/api/projects/" + projectId);

                        Platform.runLater(() -> {
                            if (httpResponse.statusCode() == 200 || httpResponse.statusCode() == 204) {
                                if (projectId.equals(selectedProjectId)) {
                                    handleGoHome();
                                }
                                loadProjects();
                                reloadTasks();
                                if (trashController != null) trashController.refresh();
                            } else {
                                showAlert("Error", "No se pudo eliminar el proyecto");
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() ->
                                showAlert("Error", "Error de conexión con el servidor"));
                    }
                }).start();
            }
        });
    }

    private void handleEditTask(Long taskId, JsonNode task) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml")
            );

            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(task);
            controller.setOnTaskUpdated(this::reloadTasks);

            Stage dialog = new Stage();
            dialog.setTitle("Editar tarea");
            dialog.setScene(new Scene(root, 500, 420));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    private void handleDeleteTask(Long taskId) {

        // Capturamos los valores actuales ANTES de abrir el diálogo
        final Long currentProjectId = selectedProjectId;
        final String currentCategory = selectedCategory;
        final String currentTitle = projectTitleLabel.getText();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar tarea");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Seguro que quieres eliminar esta tarea? Irá a la papelera.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> httpResponse = AppContext.getInstance()
                                .getApiService()
                                .delete("/api/tasks/" + taskId);

                        Platform.runLater(() -> {
                            if (httpResponse.statusCode() == 200 || httpResponse.statusCode() == 204) {
                                if (currentProjectId != null) {
                                    loadTasksForProject(currentProjectId);
                                } else if (currentCategory != null) {
                                    loadTasksByCategory(currentCategory,currentTitle);
                                } else {
                                    loadHome();
                                }
                                if (trashController != null) trashController.refresh();
                            } else {
                                showAlert("Error", "No se pudo eliminar la tarea");
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() ->
                                showAlert("Error", "Error de conexión con el servidor"));
                    }
                }).start();
            }
        });
    }

    @FXML
    private void handleProjectStatusFilter() {
        String selected = projectStatusFilter.getValue();
        if (selected == null || selected.equals("Todos")) {
            loadProjects();
            return;
        }
        filterProjectCards();
    }

    @FXML
    private void handleProjectPriorityFilter() {
        String selected = projectPriorityFilter.getValue();
        if (selected == null || selected.equals("Todas")) {
            loadProjects();
            return;
        }
        filterProjectCards();
    }

    private void filterProjectCards() {
        String status = projectStatusFilter.getValue();
        String priority = projectPriorityFilter.getValue();

        // Filtramos los proyectos ya cargados visualmente
        projectCardsContainer.getChildren().forEach(node -> {
            if (node instanceof HBox) {
                HBox row = (HBox) node;
                Object statusData = row.getProperties().get("status");
                Object priorityData = row.getProperties().get("priority");

                boolean statusMatch = status == null || status.equals("Todos") ||
                        status.equals(statusData);
                boolean priorityMatch = priority == null || priority.equals("Todas") ||
                        priority.equals(priorityData);

                row.setVisible(statusMatch && priorityMatch);
                row.setManaged(statusMatch && priorityMatch);
            }
        });
    }

    private void loadHome() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/tasks/home");

                if (response.statusCode() == 200) {
                    JsonNode home = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderHome(home));
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudo cargar el home"));
            }
        }).start();
    }

    private void loadTasksByCategory(String category, String title) {
        selectedProjectId = null;
        selectedCategory = category;
        projectTitleLabel.setText(title);
        newTaskButton.setDisable(false);
        emptyLabel.setVisible(false);

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/tasks/category/" + category);

                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    private void loadTasksForProject(Long projectId) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/tasks?projectId=" + projectId);

                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    @FXML
    private void handleStatusFilter() {
        String selected = statusFilter.getValue();
        if (selected == null || selected.equals("Todos")) {
            reloadTasks();
            return;
        }

        if (selectedProjectId != null) {
            new Thread(() -> {
                try {
                    HttpResponse<String> response = AppContext.getInstance()
                            .getApiService()
                            .get("/api/tasks/filter/status?projectId=" + selectedProjectId +
                                    "&status=" + selected);

                    if (response.statusCode() == 200) {
                        JsonNode tasks = objectMapper.readTree(response.body());
                        Platform.runLater(() -> renderTasks(tasks));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Error", "No se pudieron filtrar las tareas"));
                }
            }).start();
        } else {
            filterTaskCardsByStatus(selected); // Filtro en cliente para home y categorías
        }
    }

    @FXML
    private void handlePriorityFilter() {
        String selected = priorityFilter.getValue();
        if (selected == null || selected.equals("Todas")) {
            reloadTasks();
            return;
        }

        if (selectedProjectId != null) {
            new Thread(() -> {
                try {
                    HttpResponse<String> response = AppContext.getInstance()
                            .getApiService()
                            .get("/api/tasks/filter/priority?projectId=" + selectedProjectId +
                                    "&priority=" + selected);

                    if (response.statusCode() == 200) {
                        JsonNode tasks = objectMapper.readTree(response.body());
                        Platform.runLater(() -> renderTasks(tasks));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Error", "No se pudieron filtrar las tareas"));
                }
            }).start();
        } else {
            filterTaskCardsByPriority(selected); // Filtro en cliente para home y categorías
        }
    }

    @FXML
    private void handleTrash() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/trash-view.fxml")
            );

            VBox trashView = loader.load();
            HBox.setHgrow(trashView, Priority.ALWAYS);
            TrashController controller = loader.getController();
            trashController = controller;

            // Cuando se restaure algo recargamos el home y los proyectos
            controller.setOnTrashChanged(() -> {
                loadProjects();
                reloadTasks();
            });

            // Obtenemos el HBox central y reemplazamos solo el panel derecho (índice 1)
            javafx.scene.layout.BorderPane root = (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
            HBox centerHBox = (HBox) root.getCenter();
            centerHBox.getChildren().set(1, trashView);

        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir la papelera");
        }
    }

    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/settings-view.fxml")
            );
            VBox settingsView = loader.load();
            HBox.setHgrow(settingsView, Priority.ALWAYS);

            javafx.scene.layout.BorderPane root = (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
            HBox centerHBox = (HBox) root.getCenter();
            centerHBox.getChildren().set(1, settingsView);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        AppContext.getInstance().logout();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml")
            );

            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
            stage.setTitle("TaskMaster");
        } catch (IOException e) {
            showAlert("Error", "No se pudo cerrar la sesión");
        }
    }

    private void filterTaskCardsByStatus(String status) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof HBox) {
                HBox card = (HBox) node;
                Object cardStatus = card.getProperties().get("status");
                boolean match = status.equals(cardStatus);
                card.setVisible(match);
                card.setManaged(match);
            }
        });
    }

    private void filterTaskCardsByPriority(String priority) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof HBox) {
                HBox card = (HBox) node;
                Object cardPriority = card.getProperties().get("priority");
                boolean match = priority.equals(cardPriority);
                card.setVisible(match);
                card.setManaged(match);
            }
        });
    }

    private void reloadTasks() {
        if (selectedProjectId != null) {
            loadTasksForProject(selectedProjectId);
        } else if (selectedCategory != null) {
            loadTasksByCategory(selectedCategory, projectTitleLabel.getText());
        } else {
            loadHome();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
