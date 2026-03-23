package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NEWTASKCONTROLLER
 *
 * Controlador del diálogo de nueva tarea.
 * El proyecto es opcional - si no se selecciona, la tarea es personal.
 */
public class NewTaskController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> projectCombo;
    @FXML private VBox projectBox;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private VBox categoryBox;
    @FXML private DatePicker dueDatePicker;
    @FXML private Label errorLabel;

    // Proyecto preseleccionado si se abre desde un proyecto concreto
    private Long preSelectedProjectId;
    private Runnable onTaskCreated;

    private Long parentTaskId;

    private final List<Long> projectIds = new ArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setProjectId(Long projectId) {
        this.preSelectedProjectId = projectId;
    }

    public void setOnTaskCreated(Runnable callback) {
        this.onTaskCreated = callback;
    }

    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList(
                "BAJA", "MEDIA", "ALTA", "URGENTE"
        ));
        priorityCombo.setValue("MEDIA");

        categoryCombo.setItems(FXCollections.observableArrayList(
                "PERSONAL", "ESTUDIOS", "TRABAJO"
        ));
        categoryCombo.setValue("PERSONAL");

        // Ocultamos el combo de categoría si hay proyecto preseleccionado
        // porque la categoría se hereda del proyecto
        projectCombo.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldVal, newVal) -> {
                    int index = newVal.intValue();
                    boolean hasProject = index > 0;
                    categoryBox.setVisible(!hasProject);
                    categoryBox.setManaged(!hasProject);
                }
        );
    }

    public void initData(Long projectId) {
        this.preSelectedProjectId = projectId;
        loadProjects();

        // Si viene desde un proyecto ocultamos el combo de proyecto
        if (projectId != null) {
            projectBox.setVisible(false);
            projectBox.setManaged(false);
            categoryBox.setVisible(false);
            categoryBox.setManaged(false);
        }
    }

    public void setPreSelectedCategory(String category) {
        Platform.runLater(() -> categoryCombo.setValue(category));
    }

    public void setParentTaskId(Long parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    /**
     * Carga los proyectos del usuario y los añade al combo.
     * La primera opción es siempre "Sin proyecto (tarea personal)".
     */
    private void loadProjects() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/projects");

                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());

                    List<String> names = new ArrayList<>();
                    List<Long> ids = new ArrayList<>();

                    // Primera opción: sin proyecto
                    names.add("Sin proyecto");
                    ids.add(null);

                    for (JsonNode project : projects) {
                        names.add(project.get("name").asText());
                        ids.add(project.get("id").asLong());
                    }

                    Platform.runLater(() -> {
                        projectIds.clear();
                        projectIds.addAll(ids);
                        projectCombo.setItems(FXCollections.observableArrayList(names));

                        // Si hay proyecto preseleccionado lo marcamos
                        if (preSelectedProjectId != null) {
                            int index = ids.indexOf(preSelectedProjectId);
                            if (index >= 0) projectCombo.getSelectionModel().select(index);
                        } else {
                            // Por defecto seleccionamos "Sin proyecto"
                            projectCombo.getSelectionModel().select(0);
                        }
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error al cargar los proyectos"));
            }
        }).start();
    }

    @FXML
    private void handleCreate() {
        String title = titleField.getText().trim();
        String description = descriptionField.getText().trim();
        String priority = priorityCombo.getValue();
        LocalDate dueDate = dueDatePicker.getValue();

        if (title.isEmpty()) {
            showError("El título de la tarea es obligatorio");
            return;
        }

        // Obtenemos el projectId seleccionado (puede ser null si es personal)
        int selectedIndex = projectCombo.getSelectionModel().getSelectedIndex();
        Long projectId = (selectedIndex >= 0 && selectedIndex < projectIds.size())
                ? projectIds.get(selectedIndex)
                : null;

        new Thread(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("title", title);
                body.put("description", description);
                body.put("priority", priority);
                if (projectId != null) {
                    body.put("projectId", projectId);
                }
                if (dueDate != null) {
                    body.put("dueDate", dueDate.toString());
                }
                if (projectId == null) {
                    body.put("category", categoryCombo.getValue());
                }
                if (parentTaskId != null) {
                    body.put("parentTaskId", parentTaskId);
                }

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .postWithAuth("/api/tasks", body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 201) {
                        if (onTaskCreated != null) onTaskCreated.run();
                        closeDialog();
                    } else {
                        showError("Error al cargar la tarea");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                        showError("Error de conexión con el servidor"));
            }
        }).start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
