package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
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

    public void setOnSubtaskCreated(Runnable callback) {
        this.onSubtaskCreated = callback;
    }

    /**
     * Inicializa el diálogo con los datos heredados de la tarea padre.
     */
    public void initData(Long parentTaskId, Long projectId, String category) {
        this.parentTaskId = parentTaskId;
        this.projectId    = projectId;
        this.category     = category;
    }

    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList(
                "BAJA", "MEDIA", "ALTA", "URGENTE"));
        priorityCombo.setValue("MEDIA");

        titleField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) handleCreate();
        });
    }

    @FXML
    private void handleCreate() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("El título es obligatorio");
            return;
        }

        hideError();

        Map<String, Object> body = new HashMap<>();
        body.put("title",        title);
        body.put("description",  descriptionField.getText().trim());
        String priority = switch (priorityCombo.getValue()) {
            case "BAJA"    -> "LOW";
            case "MEDIA"   -> "MEDIUM";
            case "ALTA"    -> "HIGH";
            case "URGENTE" -> "URGENT";
            default        -> "MEDIUM";
        };
        body.put("priority", priority);
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
                        showError("Error al crear la subtarea");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error de conexión con el servidor"));
            }
        }).start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void closeDialog() {
        titleField.getScene().getWindow().hide();
    }
}
