package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
 * EDITTASKCONTROLLER
 *
 * Controlador del diálogo de editar tarea.
 * Recibe los datos actuales de la tarea y los muestra en el formulario.
 */
public class EditTaskController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private DatePicker dueDatePicker;
    @FXML private Label errorLabel;

    private Long taskId;
    private Runnable onTaskUpdated;

    public void setOnTaskUpdated(Runnable callback) {
        this.onTaskUpdated = callback;
    }

    @FXML
    public void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList(
                "TODO", "IN_PROGRESS", "DONE", "CANCELLED"
        ));
        priorityCombo.setItems(FXCollections.observableArrayList(
                "LOW", "MEDIUM", "HIGH", "URGENT"
        ));
    }

    /**
     * Rellena el formulario con los datos actuales de la tarea.
     */
    public void initData(JsonNode task) {
        this.taskId = task.get("id").asLong();
        titleField.setText(task.get("title").asText());

        if (task.has("description") && !task.get("description").isNull()) {
            descriptionField.setText(task.get("description").asText());
        }

        statusCombo.setValue(task.get("status").asText());
        priorityCombo.setValue(task.get("priority").asText());

        if (task.has("dueDate") && !task.get("dueDate").isNull()) {
            dueDatePicker.setValue(LocalDate.parse(task.get("dueDate").asText()));
        }
    }

    @FXML
    private void handleSave() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("El título es obligatorio");
            return;
        }

        new Thread(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("title", title);
                body.put("description", descriptionField.getText().trim());
                body.put("status", statusCombo.getValue());
                body.put("priority", priorityCombo.getValue());
                if (dueDatePicker.getValue() != null) {
                    body.put("dueDate", dueDatePicker.getValue().toString());
                }

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .put("/api/tasks/" + taskId, body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        if (onTaskUpdated != null) onTaskUpdated.run();
                        closeDialog();
                    } else {
                        showError("Error al guardar los cambios");
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

    private void closeDialog() {
        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
