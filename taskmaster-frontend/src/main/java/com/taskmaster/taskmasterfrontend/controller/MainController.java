package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        projectListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        int index = projectListView.getSelectionModel().getSelectedIndex();

                    }
                }
        );
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
                        projectListView.setItems(FXCollections.observableArrayList(names));

                        // Guardamos los ids para usarlos al seleccionar un proyecto
                        projectListView.setUserData(ids);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudieron cargar los proyectos"));
            }
        }).start();
    }

    /**
     * Carga las tareas del proyecto seleccionado.
     */
    @SuppressWarnings("unchecked")
    private void loadProjectTasks(int projectIndex) {
        var ids = (java.util.ArrayList<Long>) projectListView.getUserData();
        if (ids == null || projectIndex >= ids.size()) return;

        selectedProjectId = ids.get(projectIndex);
        String projectName = projectListView.getItems().get(projectIndex);

        projectTitleLabel.setText(projectName);
        newTaskButton.setDisable(false);
        emptyLabel.setVisible(false);

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/tasks?projectId=" + selectedProjectId);

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

    /**
     * Renderiza las tareas en el panel derecho.
     */
    private void renderTasks(JsonNode tasks) {
        // Limpiamos el contenedor manteniendo el label vacío
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);

        if (!tasks.isArray() || tasks.size() == 0) {
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

        // Checkbox de completado
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(status.equals("DONE"));

        // Título de la tarea
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2d2d2d;");
        if (status.equals("DONE")) {
            titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaaaaa; " +
                    "-fx-strikethrough: true;");
        }
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Badge de prioridad
        Label priorityBadge = new Label(priority);
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        card.getChildren().addAll(checkBox, titleLabel, priorityBadge);
        return card;
    }

    /**
     * Devuelve el color del badge según la prioridad.
     */
    private String getPriorityColor(String priority) {
        return switch (priority) {
            case "URGENT" -> "#e74c3c";
            case "HIGH" -> "#e67e22";
            case "MEDIUM" -> "#3498db";
            case "LOW" -> "#95a5a6";
            default -> "#95a5a6";
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
        // TODO: abrir diálogo de nueva tarea
        showAlert("Próximamente", "Crear tarea - en desarrollo");
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
