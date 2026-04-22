package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * NEWPROJECTCONTROLLER
 *
 * Controlador del diálogo de nuevo proyecto.
 * Al crear el proyecto notifica al MainController para
 * que recargue la lista de proyectos automáticamente.
 */
public class NewProjectController {

    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private Label errorLabel;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> priorityCombo;

    // Callback que se ejecuta cuando el proyecto se crea correctamente
    // Permite notificar al MainController sin acoplamiento directo
    private Runnable onProjectCreated;

    private final LanguageManager lm = LanguageManager.getInstance();

    public void setOnProjectCreated(Runnable callback) {
        this.onProjectCreated = callback;
    }

    @FXML
    private void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                lm.get("category.PERSONAL"), lm.get("category.ESTUDIOS"), lm.get("category.TRABAJO")));
        categoryCombo.setValue(lm.get("category.PERSONAL"));

        statusCombo.setItems(FXCollections.observableArrayList(
                lm.get("status.todo"), lm.get("status.inprogress"),
                lm.get("status.done"), lm.get("status.cancelled")));
        statusCombo.setValue(lm.get("status.todo"));

        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low"), lm.get("priority.medium"),
                lm.get("priority.high"), lm.get("priority.urgent")));
        priorityCombo.setValue(lm.get("priority.medium"));
    }

    @FXML
    private void handleCreate() {
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();

        if (name.isEmpty()) {
            showError(lm.get("new.project.error.name")); return;
        }

        new Thread(() -> {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("name", name);
                params.put("description", description);

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .postWithAuthNoBody("/api/projects?name=" +
                                java.net.URLEncoder.encode(name, StandardCharsets.UTF_8) + "&description=" +
                                java.net.URLEncoder.encode(description, StandardCharsets.UTF_8) +
                                "&category=" + categoryToEnum(categoryCombo.getValue()) +
                                "&status="   + statusToEnum(statusCombo.getValue()) +
                                "&priority=" + priorityToEnum(priorityCombo.getValue()));

                javafx.application.Platform.runLater(() -> {
                    if (response.statusCode() == 201) {
                        // Notificamos al MainController y cerramos el diálogo
                        if (onProjectCreated != null) onProjectCreated.run();
                        closeDialog();
                    } else {
                        showError(lm.get("new.project.error.create"));
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        nameField.getScene().getWindow().hide();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private String categoryToEnum(String label) {
        if (label.equals(lm.get("category.PERSONAL"))) return "PERSONAL";
        if (label.equals(lm.get("category.ESTUDIOS"))) return "ESTUDIOS";
        if (label.equals(lm.get("category.TRABAJO")))  return "TRABAJO";
        return label;
    }

    private String statusToEnum(String label) {
        if (label.equals(lm.get("status.todo")))       return "TODO";
        if (label.equals(lm.get("status.inprogress"))) return "IN_PROGRESS";
        if (label.equals(lm.get("status.done")))       return "DONE";
        if (label.equals(lm.get("status.cancelled")))  return "CANCELLED";
        return label;
    }

    private String priorityToEnum(String label) {
        if (label.equals(lm.get("priority.low")))    return "LOW";
        if (label.equals(lm.get("priority.medium"))) return "MEDIUM";
        if (label.equals(lm.get("priority.high")))   return "HIGH";
        if (label.equals(lm.get("priority.urgent"))) return "URGENT";
        return label;
    }
}
