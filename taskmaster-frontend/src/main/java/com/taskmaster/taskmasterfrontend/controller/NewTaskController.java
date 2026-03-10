package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * NEWTASKCONTROLLER
 *
 * Controlador del diálogo de nueva tarea.
 */
public class NewTaskController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private DatePicker dueDatePicker;
    @FXML private Label errorLabel;

    private Long projectId;
    private Runnable onTaskCreated;

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public void setOnTaskCreated(Runnable callback) {
        this.onTaskCreated = callback;
    }

    @FXML
    public void initialize() {
        priorityCombo.setItems(FXCollections.observableArrayList(
                "LOW", "MEDIUM", "HIGH", "URGENT"
        ));
        priorityCombo.setValue("MEDIUM");
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

        new Thread(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("title", title);
                body.put("description", description);
                body.put("priority", priority);
                body.put("projectId", projectId);
                if (dueDate != null) {
                    body.put("dueDate", dueDate.toString());
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
