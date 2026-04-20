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
                lm.get("priority.low.label"), lm.get("priority.medium.label"),
                lm.get("priority.high.label"), lm.get("priority.urgent.label")));
        priorityCombo.setValue(lm.get("priority.medium.label"));

        titleField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) handleCreate();
        });
    }

    @FXML
    private void handleCreate() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError(lm.get("new.subtask.error.title"));
            return;
        }
        hideError();

        String p = priorityCombo.getValue();
        String priorityEnum;
        if (p.equals(lm.get("priority.low.label")))         priorityEnum = "LOW";
        else if (p.equals(lm.get("priority.medium.label"))) priorityEnum = "MEDIUM";
        else if (p.equals(lm.get("priority.high.label")))   priorityEnum = "HIGH";
        else if (p.equals(lm.get("priority.urgent.label"))) priorityEnum = "URGENT";
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
