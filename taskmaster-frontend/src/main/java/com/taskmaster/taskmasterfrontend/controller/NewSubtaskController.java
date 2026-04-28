package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador del diálogo de creación de nueva subtarea.
 *
 * <p>Hereda el identificador de la tarea padre, el proyecto y la categoría
 * de la tarea que la contiene. Si la tarea padre pertenece a un proyecto,
 * la subtarea se asocia al mismo proyecto; si no, hereda la categoría.</p>
 *
 * @author Carlos
 */
public class NewSubtaskController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private Label errorLabel;

    private Long parentTaskId;
    private Long projectId;
    private String category;
    private Runnable onSubtaskCreated;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Registra el callback que se ejecutará tras crear la subtarea correctamente.
     *
     * @param callback Acción a ejecutar al completar la creación.
     */
    public void setOnSubtaskCreated(Runnable callback) {
        this.onSubtaskCreated = callback;
    }

    /**
     * Establece los datos heredados de la tarea padre.
     *
     * @param parentTaskId Identificador de la tarea padre.
     * @param projectId    Identificador del proyecto asociado, o {@code null} si no tiene.
     * @param category     Categoría de la tarea padre, usada si no hay proyecto.
     */
    public void initData(Long parentTaskId, Long projectId, String category) {
        this.parentTaskId = parentTaskId;
        this.projectId    = projectId;
        this.category     = category;
    }

    /**
     * Inicializa el combo de prioridad con sus valores localizados
     * y configura el envío del formulario con la tecla Enter.
     */
    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low"), lm.get("priority.medium"),
                lm.get("priority.high"), lm.get("priority.urgent")));
        priorityCombo.setValue(lm.get("priority.medium"));

        titleField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) handleCreate();
        });
    }

    /**
     * Valida el formulario y envía la solicitud de creación de la subtarea
     * al backend. Si la operación es exitosa, ejecuta el callback y cierra el diálogo.
     */
    @FXML
    private void handleCreate() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError(lm.get("common.error.title.required"));
            return;
        }
        hideError();

        String p = priorityCombo.getValue();
        String priorityEnum;
        if (p.equals(lm.get("priority.low")))         priorityEnum = "LOW";
        else if (p.equals(lm.get("priority.medium"))) priorityEnum = "MEDIUM";
        else if (p.equals(lm.get("priority.high")))   priorityEnum = "HIGH";
        else if (p.equals(lm.get("priority.urgent"))) priorityEnum = "URGENT";
        else priorityEnum = "MEDIUM";

        Map<String, Object> body = new HashMap<>();
        body.put("title",       title);
        body.put("description", descriptionField.getText().trim());
        body.put("priority",    priorityEnum);
        body.put("parentTaskId", parentTaskId);

        if (projectId != null) {
            body.put("projectId", projectId);
        } else if (category != null) {
            body.put("category", category);
        }

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().postWithAuth("/api/tasks", body);
                Platform.runLater(() -> {
                    if (response.statusCode() == 201) {
                        if (onSubtaskCreated != null) onSubtaskCreated.run();
                        closeDialog();
                    } else {
                        showError(lm.get("new.subtask.error.create"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();
    }

    /**
     * Cierra el diálogo sin crear la subtarea.
     */
    @FXML
    private void handleCancel() {
        closeDialog();
    }

    /**
     * Muestra un mensaje de error en la etiqueta de error del formulario.
     *
     * @param msg Mensaje de error a mostrar.
     */
    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /**
     * Oculta la etiqueta de error del formulario.
     */
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /**
     * Cierra el diálogo actual.
     */
    private void closeDialog() {
        titleField.getScene().getWindow().hide();
    }
}
