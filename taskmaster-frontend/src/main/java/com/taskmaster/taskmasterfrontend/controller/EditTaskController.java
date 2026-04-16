package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

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
    @FXML private Label dialogTitleLabel;

    private Runnable onCancel;
    private Long taskId;
    private Runnable onTaskUpdated;

    public void setOnTaskUpdated(Runnable callback) {
        this.onTaskUpdated = callback;
    }

    public void setDialogTitle(String title) {
        dialogTitleLabel.setText(title);
    }

    @FXML
    public void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList(
                "PENDIENTE", "EN CURSO", "COMPLETADA", "ENTREGADA", "CANCELADA"
        ));
        statusCombo.setValue("PENDIENTE");

        priorityCombo.setItems(FXCollections.observableArrayList(
                "BAJA", "MEDIA", "ALTA", "URGENTE"
        ));
        priorityCombo.setValue("MEDIA");
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
        statusCombo.setValue(translateStatus(task.get("status").asText()));
        priorityCombo.setValue(translatePriority(task.get("priority").asText()));
        if (task.has("dueDate") && !task.get("dueDate").isNull()) {
            dueDatePicker.setValue(LocalDate.parse(task.get("dueDate").asText()));
        }

        Platform.runLater(() -> {
            titleField.deselect();
            titleField.positionCaret(0);
            titleField.getParent().requestFocus();
        });
    }

    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
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
                body.put("status",   reverseStatus(statusCombo.getValue()));
                body.put("priority", reversePriority(priorityCombo.getValue()));
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
        if (onCancel != null) onCancel.run();
        else closeDialog();
    }

    private void closeDialog() {
        titleField.getScene().getWindow().hide();
    }

    private String translateStatus(String s) {
        return switch (s) {
            case "TODO"        -> "PENDIENTE";
            case "IN_PROGRESS" -> "EN CURSO";
            case "DONE"        -> "COMPLETADA";
            case "SUBMITTED" -> "ENTREGADA";
            case "CANCELLED"   -> "CANCELADA";
            default            -> s;
        };
    }

    private String translatePriority(String p) {
        return switch (p) {
            case "LOW"    -> "BAJA";
            case "MEDIUM" -> "MEDIA";
            case "HIGH"   -> "ALTA";
            case "URGENT" -> "URGENTE";
            default       -> p;
        };
    }

    private String reverseStatus(String s) {
        return switch (s) {
            case "PENDIENTE"   -> "TODO";
            case "EN CURSO" -> "IN_PROGRESS";
            case "COMPLETADA"  -> "DONE";
            case "ENTREGADA" -> "SUBMITTED";
            case "CANCELADA"   -> "CANCELLED";
            default            -> s;
        };
    }

    private String reversePriority(String p) {
        return switch (p) {
            case "BAJA"    -> "LOW";
            case "MEDIA"   -> "MEDIUM";
            case "ALTA"    -> "HIGH";
            case "URGENTE" -> "URGENT";
            default        -> p;
        };
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
