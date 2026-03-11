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
    @FXML private ListView<String> projectListView;
    @FXML private Label projectTitleLabel;
    @FXML private VBox taskContainer;
    @FXML private Label emptyLabel;
    @FXML private Button newTaskButton;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> priorityFilter;

    // ID del proyecto seleccionado actualmente
    private Long selectedProjectId;

    // ObjectMapper para parsear JSON
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final List<Long> projectIds = new ArrayList<>();

    private String selectedCategory;

    @FXML
    public void initialize() {
        // Mostramos el nombre del usuario autenticado
        usernameLabel.setText(AppContext.getInstance().getCurrentUsername());

        statusFilter.setItems(FXCollections.observableArrayList(
                "Todos", "TODO", "IN_PROGRESS", "DONE", "CANCELLED"
        ));
        priorityFilter.setItems(FXCollections.observableArrayList(
                "Todas", "LOW", "MEDIUM", "HIGH", "URGENT"
        ));

        projectListView.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldIndex, newIndex) -> {
                    int index = newIndex.intValue();
                    if (index >= 0 && index < projectIds.size()) {
                        selectedProjectId = projectIds.get(index);
                        String projectName = projectListView.getItems().get(index);
                        projectTitleLabel.setText(projectName);
                        newTaskButton.setDisable(false);
                        emptyLabel.setVisible(false);
                        loadTasksForProject(selectedProjectId);
                    }
                }
        );

        loadProjects();
    }

    /**
     * Carga los proyectos del usuario desde el backend.
     */
    private void loadProjects() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/projects");

                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());

                    // Guardamos los ids y nombres de los proyectos
                    var names = new java.util.ArrayList<String>();
                    var ids = new java.util.ArrayList<Long>();

                    for (JsonNode project : projects) {
                        names.add(project.get("name").asText());
                        ids.add(project.get("id").asLong());
                    }

                    Platform.runLater(() -> {
                        projectIds.clear();
                        projectIds.addAll(ids);
                        projectListView.setItems(FXCollections.observableArrayList(names));
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudieron cargar los proyectos"));
            }
        }).start();
    }

    /**
     * Renderiza las tareas en el panel derecho.
     */
    private void renderTasks(JsonNode tasks) {
        // Limpiamos el contenedor manteniendo el label vacío
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);

        if (!tasks.isArray() || tasks.isEmpty()) {
            emptyLabel.setText("No hay tareas en este proyecto");
            emptyLabel.setVisible(true);
            return;
        }

        emptyLabel.setVisible(false);

        for (JsonNode task : tasks) {
            taskContainer.getChildren().add(createTaskCard(task));
        }
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

        card.getChildren().addAll(checkBox, titleLabel, priorityBadge);
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
            controller.setOnProjectCreated(this::loadProjects);

            Stage dialog = new Stage();
            dialog.setTitle("Nuevo proyecto");
            dialog.setScene(new Scene(root, 400, 300));
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

            controller.setProjectId(selectedProjectId);
            controller.setOnTaskCreated(this::reloadTasks);

            Stage dialog = new Stage();
            dialog.setTitle("Nueva tarea");
            dialog.setScene(new Scene(root, 420, 420));
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
        // TODO: filtrar tareas por estado
    }

    @FXML
    private void handlePriorityFilter() {
        // TODO: filtrar tareas por prioridad
    }

    @FXML
    private void handleTrash() {
        // TODO: abrir pantalla de papelera
        showAlert("Próximamente", "Papelera - en desarrollo");
    }

    @FXML
    private void handleSettings() {
        // TODO: abrir pantalla de ajustes
        showAlert("Próximamente", "Ajustes - en desarrollo");
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

    private void reloadTasks() {
        if (selectedProjectId != null) {
            loadTasksForProject(selectedProjectId);
        } else if (selectedCategory != null) {
            loadTasksByCategory(selectedCategory, projectTitleLabel.getText());
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
