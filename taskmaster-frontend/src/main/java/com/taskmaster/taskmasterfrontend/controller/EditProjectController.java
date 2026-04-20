package com.taskmaster.taskmasterfrontend.controller;

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

public class EditProjectController {

    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private Label errorLabel;

    private Long projectId;
    private Runnable onProjectUpdated;

    private final LanguageManager lm = LanguageManager.getInstance();

    public void setOnProjectUpdated(Runnable callback) {
        this.onProjectUpdated = callback;
    }

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                lm.get("category.PERSONAL"), lm.get("category.ESTUDIOS"), lm.get("category.TRABAJO")));
        categoryCombo.setValue(lm.get("category.PERSONAL"));

        statusCombo.setItems(FXCollections.observableArrayList(
                lm.get("status.todo.label"), lm.get("status.inprogress.label"),
                lm.get("status.done.project.label"), lm.get("status.cancelled.project.label")));
        statusCombo.setValue(lm.get("status.todo.label"));

        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low.label"), lm.get("priority.medium.label"),
                lm.get("priority.high.label"), lm.get("priority.urgent.label")));
        priorityCombo.setValue(lm.get("priority.medium.label"));
    }

    public void initData(Long projectId, String projectName) {
        this.projectId = projectId;
        nameField.setText(projectName);

        // Cargamos los datos completos del proyecto desde el backend
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/projects/" + projectId);

                if (response.statusCode() == 200) {
                    com.fasterxml.jackson.databind.JsonNode project =
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(response.body());

                    javafx.application.Platform.runLater(() -> {
                        if (project.has("description") && !project.get("description").isNull()) {
                            descriptionField.setText(project.get("description").asText());
                        }
                        if (project.has("category") && !project.get("category").isNull()) {
                            String cat = project.get("category").asText();
                            categoryCombo.setValue(switch (cat) {
                                case "ESTUDIOS" -> lm.get("category.ESTUDIOS");
                                case "TRABAJO"  -> lm.get("category.TRABAJO");
                                default         -> lm.get("category.PERSONAL");
                            });
                        }
                        if (project.has("status") && !project.get("status").isNull()) {
                            String status = project.get("status").asText();
                            statusCombo.setValue(switch (status) {
                                case "IN_PROGRESS" -> lm.get("status.inprogress.label");
                                case "DONE"        -> lm.get("status.done.project.label");
                                case "CANCELLED"   -> lm.get("status.cancelled.project.label");
                                default            -> lm.get("status.todo.label");
                            });
                        }
                        if (project.has("priority") && !project.get("priority").isNull()) {
                            String priority = project.get("priority").asText();
                            priorityCombo.setValue(switch (priority) {
                                case "LOW"    -> lm.get("priority.low.label");
                                case "HIGH"   -> lm.get("priority.high.label");
                                case "URGENT" -> lm.get("priority.urgent.label");
                                default       -> lm.get("priority.medium.label");
                            });
                        }
                        nameField.deselect();
                        nameField.getParent().requestFocus();
                    });
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showError(lm.get("edit.project.error.load")));
            }
        }).start();
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError(lm.get("edit.project.error.name"));
            return;
        }

        if (categoryCombo.getValue() == null) {
            showError(lm.get("edit.project.error.category"));
            return;
        }

        new Thread(() -> {
            try {
                String url = "/api/projects/" + projectId +
                        "?name=" + java.net.URLEncoder.encode(name, "UTF-8") +
                        "&description=" + java.net.URLEncoder.encode(
                        descriptionField.getText().trim(), "UTF-8") +
                        "&category=" + mapCategory(categoryCombo.getValue()) +
                        "&status=" + mapStatus(statusCombo.getValue()) +
                        "&priority=" + mapPriority(priorityCombo.getValue());

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .putNoBody(url);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        if (onProjectUpdated != null) onProjectUpdated.run();
                        closeDialog();
                    } else {
                        showError(lm.get("edit.project.error.save"));
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

    private void closeDialog() {
        nameField.getScene().getWindow().hide();
    }

    private String mapCategory(String label) {
        if (label.equals(lm.get("category.ESTUDIOS")))      return "ESTUDIOS";
        else if (label.equals(lm.get("category.TRABAJO")))  return "TRABAJO";
        else return "PERSONAL";
    }

    private String mapStatus(String label) {
        if (label.equals(lm.get("status.inprogress.label")))           return "IN_PROGRESS";
        else if (label.equals(lm.get("status.done.project.label")))    return "DONE";
        else if (label.equals(lm.get("status.cancelled.project.label"))) return "CANCELLED";
        else return "TODO";
    }

    private String mapPriority(String label) {
        if (label.equals(lm.get("priority.low.label")))         return "LOW";
        else if (label.equals(lm.get("priority.high.label")))   return "HIGH";
        else if (label.equals(lm.get("priority.urgent.label"))) return "URGENT";
        else return "MEDIUM";
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
