package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

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
    private final LanguageManager lm = LanguageManager.getInstance();

    public void setProjectId(Long projectId) {
        this.preSelectedProjectId = projectId;
    }

    public void setOnTaskCreated(Runnable callback) {
        this.onTaskCreated = callback;
    }

    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low"), lm.get("priority.medium"),
                lm.get("priority.high"), lm.get("priority.urgent")));
        priorityCombo.setValue(lm.get("priority.medium"));

        categoryCombo.setItems(FXCollections.observableArrayList(
                lm.get("category.PERSONAL"), lm.get("category.ESTUDIOS"), lm.get("category.TRABAJO")));
        categoryCombo.setValue(lm.get("category.PERSONAL"));

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
                    names.add(lm.get("new.task.project.prompt"));
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
                Platform.runLater(() -> showError(lm.get("new.task.error.load.projects")));
            }
        }).start();
    }

    @FXML
    private void handleCreate() {
        String title = titleField.getText().trim();
        String description = descriptionField.getText().trim();

        String priority = priorityCombo.getValue();
        String priorityEnum;
        if (priority.equals(lm.get("priority.low")))         priorityEnum = "LOW";
        else if (priority.equals(lm.get("priority.medium"))) priorityEnum = "MEDIUM";
        else if (priority.equals(lm.get("priority.high")))   priorityEnum = "HIGH";
        else if (priority.equals(lm.get("priority.urgent"))) priorityEnum = "URGENT";
        else priorityEnum = "MEDIUM";

        LocalDate dueDate = dueDatePicker.getValue();

        if (title.isEmpty()) {
            showError(lm.get("new.task.error.title"));
            return;
        }

        int selectedIndex = projectCombo.getSelectionModel().getSelectedIndex();
        Long projectId = (selectedIndex >= 0 && selectedIndex < projectIds.size())
                ? projectIds.get(selectedIndex)
                : null;

        new Thread(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("title", title);
                body.put("description", description);
                body.put("priority", priorityEnum);

                if (projectId != null) {
                    body.put("projectId", projectId);
                }
                if (dueDate != null) {
                    body.put("dueDate", dueDate.toString());
                }
                if (projectId == null) {
                    String cat = categoryCombo.getValue();
                    String catEnum;
                    if (cat.equals(lm.get("category.ESTUDIOS")))       catEnum = "ESTUDIOS";
                    else if (cat.equals(lm.get("category.TRABAJO")))    catEnum = "TRABAJO";
                    else                                                 catEnum = "PERSONAL";
                    body.put("category", catEnum);
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
                        showError(lm.get("new.task.error.create"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        titleField.getScene().getWindow().hide();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
