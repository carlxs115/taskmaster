package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador del diálogo de creación de nueva tarea.
 *
 * <p>Permite crear tareas personales o asociadas a un proyecto. Si se abre
 * desde un proyecto concreto, oculta los combos de proyecto y categoría ya
 * que ambos se heredan del proyecto.</p>
 *
 * @author Carlos
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

    private Long         preSelectedProjectId;
    private Runnable     onTaskCreated;
    private Long         parentTaskId;
    private final List<Long> projectIds = new ArrayList<>();

    private final ObjectMapper    objectMapper = new ObjectMapper();
    private final LanguageManager lm           = LanguageManager.getInstance();

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Establece el proyecto preseleccionado al abrir el diálogo desde un proyecto.
     *
     * @param projectId identificador del proyecto a preseleccionar
     */
    public void setProjectId(Long projectId) {
        this.preSelectedProjectId = projectId;
    }

    /**
     * Registra el callback que se ejecutará tras crear la tarea correctamente.
     *
     * @param callback acción a ejecutar al completar la creación
     */
    public void setOnTaskCreated(Runnable callback) {
        this.onTaskCreated = callback;
    }

    /**
     * Inicializa los combos y el listener que oculta la categoría
     * cuando se selecciona un proyecto.
     */
    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low"),
                lm.get("priority.medium"),
                lm.get("priority.high"),
                lm.get("priority.urgent")));
        priorityCombo.setValue(lm.get("priority.medium"));

        categoryCombo.setItems(FXCollections.observableArrayList(
                lm.get("category.PERSONAL"),
                lm.get("category.ESTUDIOS"),
                lm.get("category.TRABAJO")));
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

        titleField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) handleCreate();});
        descriptionField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) handleCreate();});
    }

    // -------------------------------------------------------------------------
    // Carga de datos
    // -------------------------------------------------------------------------

    /**
     * Carga los proyectos del usuario y aplica el proyecto preseleccionado si existe.
     * Si viene de un proyecto concreto, oculta los combos de proyecto y categoría.
     *
     * @param projectId identificador del proyecto preseleccionado, o {@code null}
     */
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

    /**
     * Preselecciona una categoría en el combo.
     *
     * @param category etiqueta localizada de la categoría
     */
    public void setPreSelectedCategory(String category) {
        Platform.runLater(() -> categoryCombo.setValue(category));
    }

    /**
     * Establece el identificador de la tarea padre cuando se crea una subtarea.
     *
     * @param parentTaskId identificador de la tarea padre
     */
    public void setParentTaskId(Long parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    /**
     * Obtiene los proyectos del usuario y rellena el combo.
     * La primera opción siempre es "Sin proyecto (tarea personal)".
     */
    private void loadProjects() {
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/projects");
                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());
                    List<String> names = new ArrayList<>();
                    List<Long>   ids   = new ArrayList<>();
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
                        if (preSelectedProjectId != null) {
                            int index = ids.indexOf(preSelectedProjectId);
                            if (index >= 0) projectCombo.getSelectionModel().select(index);
                        } else {
                            projectCombo.getSelectionModel().select(0);
                        }
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("new.task.error.load.projects")));
            }
        }, "new-task-load-projects");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    /**
     * Valida el formulario y envía la solicitud de creación de la tarea al backend.
     * Cierra el diálogo si la operación es exitosa.
     */
    @FXML
    private void handleCreate() {
        String title = titleField.getText().trim();
        String description = descriptionField.getText().trim();
        LocalDate dueDate = dueDatePicker.getValue();

        if (title.isEmpty()) {
            showError(lm.get("new.task.error.title"));
            return;
        }

        // Resolvemos el proyecto: preseleccionado o elegido en el combo
        Long projectId;
        if (preSelectedProjectId != null) {
            projectId = preSelectedProjectId;
        } else {
            int index = projectCombo.getSelectionModel().getSelectedIndex();
            projectId = (index >= 0 && index < projectIds.size()) ? projectIds.get(index) : null;
        }

        // Convertimos la prioridad localizada a su código de backend
        String priorityEnum = priorityToEnum(priorityCombo.getValue());
        final Long finalProjectId = projectId;

        Thread t = new Thread(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("title",       title);
                body.put("description", description);
                body.put("priority",    priorityEnum);
                if (finalProjectId != null) {
                    body.put("projectId", finalProjectId);
                } else {
                    // Sin proyecto: usamos la categoría seleccionada
                    body.put("category", categoryToEnum(categoryCombo.getValue()));
                }
                if (dueDate != null) body.put("dueDate", dueDate.toString());
                if (parentTaskId != null) body.put("parentTaskId", parentTaskId);

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().postWithAuth("/api/tasks", body);

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
        }, "new-task-create");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Cierra el diálogo sin crear la tarea.
     */
    @FXML
    private void handleCancel() {
        closeDialog();
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Cierra el diálogo actual.
     */
    private void closeDialog() {
        titleField.getScene().getWindow().hide();
    }

    /**
     * Muestra un mensaje de error en la etiqueta de error del formulario.
     *
     * @param message Mensaje de error a mostrar.
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private String priorityToEnum(String label) {
        if (label.equals(lm.get("priority.low"))) return "LOW";
        if (label.equals(lm.get("priority.medium"))) return "MEDIUM";
        if (label.equals(lm.get("priority.high"))) return "HIGH";
        if (label.equals(lm.get("priority.urgent"))) return "URGENT";
        return "MEDIUM";
    }

    private String categoryToEnum(String label) {
        if (label.equals(lm.get("category.ESTUDIOS"))) return "ESTUDIOS";
        if (label.equals(lm.get("category.TRABAJO"))) return "TRABAJO";
        return "PERSONAL";
    }
}