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
 * Controlador del diálogo de creación de nueva tarea.
 *
 * <p>Permite crear tareas personales o asociadas a un proyecto. Si se abre
 * desde un proyecto concreto, oculta los combos de proyecto y categoría ya
 * que ambos se heredan del proyecto. El proyecto es siempre opcional: si no
 * se selecciona, la tarea se clasifica por categoría.</p>
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

    // Proyecto preseleccionado si se abre desde un proyecto concreto
    private Long preSelectedProjectId;
    private Runnable onTaskCreated;

    private Long parentTaskId;

    private final List<Long> projectIds = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Establece el proyecto preseleccionado al abrir el diálogo desde
     * la vista de un proyecto concreto.
     *
     * @param projectId Identificador del proyecto a preseleccionar.
     */
    public void setProjectId(Long projectId) {
        this.preSelectedProjectId = projectId;
    }

    /**
     * Registra el callback que se ejecutará tras crear la tarea correctamente.
     *
     * @param callback Acción a ejecutar al completar la creación.
     */
    public void setOnTaskCreated(Runnable callback) {
        this.onTaskCreated = callback;
    }

    /**
     * Inicializa los combos de prioridad y categoría con sus valores localizados,
     * y configura el listener que oculta el combo de categoría cuando se
     * selecciona un proyecto.
     */
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

    /**
     * Carga los proyectos del usuario, aplica el proyecto preseleccionado si existe
     * y oculta los combos de proyecto y categoría si la tarea tiene proyecto fijo.
     *
     * @param projectId Identificador del proyecto preseleccionado, o {@code null} si no hay.
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
     * Preselecciona una categoría en el combo de categoría.
     *
     * @param category Etiqueta localizada de la categoría a seleccionar.
     */
    public void setPreSelectedCategory(String category) {
        Platform.runLater(() -> categoryCombo.setValue(category));
    }

    /**
     * Establece el identificador de la tarea padre cuando la nueva tarea
     * se crea como subtarea.
     *
     * @param parentTaskId Identificador de la tarea padre.
     */
    public void setParentTaskId(Long parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    /**
     * Obtiene los proyectos del usuario desde el backend y rellena el combo
     * de proyectos. La primera opción siempre es "Sin proyecto (tarea personal)".
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

    /**
     * Valida el formulario y envía la solicitud de creación de la tarea al backend,
     * incluyendo proyecto, categoría, prioridad, fecha límite y tarea padre si aplica.
     * Cierra el diálogo si la operación es exitosa.
     */
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

    /**
     * Cierra el diálogo sin crear la tarea.
     */
    @FXML
    private void handleCancel() {
        closeDialog();
    }

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
}
