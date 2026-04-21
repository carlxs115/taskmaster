package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
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

    private final LanguageManager lm = LanguageManager.getInstance();

    public void setOnTaskUpdated(Runnable callback) {
        this.onTaskUpdated = callback;
    }

    public void setDialogTitle(String title) {
        dialogTitleLabel.setText(title);
    }

    @FXML
    public void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList(
                lm.get("status.todo"), lm.get("status.inprogress"),
                lm.get("status.done"), lm.get("status.submitted"),
                lm.get("status.cancelled")));
        statusCombo.setValue(lm.get("status.todo"));

        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low"), lm.get("priority.medium"),
                lm.get("priority.high"), lm.get("priority.urgent")));
        priorityCombo.setValue(lm.get("priority.medium"));
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
            showError(lm.get("common.error.title.required"));
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
                        showError(lm.get("common.error.save"));
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
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
            case "TODO"        -> lm.get("status.todo");
            case "IN_PROGRESS" -> lm.get("status.inprogress");
            case "DONE"        -> lm.get("status.done.task");
            case "SUBMITTED"   -> lm.get("status.submitted");
            case "CANCELLED"   -> lm.get("status.cancelled.task");
            default            -> s;
        };
    }


    private String translatePriority(String p) {
        return switch (p) {
            case "LOW"    -> lm.get("priority.low");
            case "MEDIUM" -> lm.get("priority.medium");
            case "HIGH"   -> lm.get("priority.high");
            case "URGENT" -> lm.get("priority.urgent");
            default       -> p;
        };
    }


    private String reverseStatus(String s) {
        if (s.equals(lm.get("status.inprogress")))          return "IN_PROGRESS";
        else if (s.equals(lm.get("status.done")))      return "DONE";
        else if (s.equals(lm.get("status.submitted")))      return "SUBMITTED";
        else if (s.equals(lm.get("status.cancelled"))) return "CANCELLED";
        else return "TODO";
    }

    private String reversePriority(String p) {
        if (p.equals(lm.get("priority.low")))         return "LOW";
        else if (p.equals(lm.get("priority.high")))   return "HIGH";
        else if (p.equals(lm.get("priority.urgent"))) return "URGENT";
        else return "MEDIUM";
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
