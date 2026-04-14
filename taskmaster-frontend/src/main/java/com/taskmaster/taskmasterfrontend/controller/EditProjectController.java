package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

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

    public void setOnProjectUpdated(Runnable callback) {
        this.onProjectUpdated = callback;
    }

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                "PERSONAL", "ESTUDIOS", "TRABAJO"
        ));
        categoryCombo.setValue("PERSONAL");

        statusCombo.setItems(FXCollections.observableArrayList(
                "PENDIENTE", "EN CURSO", "COMPLETADO", "CANCELADO"
        ));
        statusCombo.setValue("PENDIENTE");

        priorityCombo.setItems(FXCollections.observableArrayList(
                "BAJA", "MEDIA", "ALTA", "URGENTE"
        ));
        priorityCombo.setValue("MEDIA");
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
                            categoryCombo.setValue(project.get("category").asText());
                        }

                        if (project.has("status") && !project.get("status").isNull()) {
                            String status = project.get("status").asText();
                            statusCombo.setValue(switch (status) {
                                case "IN_PROGRESS" -> "EN CURSO";
                                case "DONE"        -> "COMPLETADO";
                                case "CANCELLED"   -> "CANCELADO";
                                default            -> "PENDIENTE";
                            });
                        }
                        if (project.has("priority") && !project.get("priority").isNull()) {
                            String priority = project.get("priority").asText();
                            priorityCombo.setValue(switch (priority) {
                                case "LOW"    -> "BAJA";
                                case "HIGH"   -> "ALTA";
                                case "URGENT" -> "URGENTE";
                                default       -> "MEDIA";
                            });
                        }
                    });
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showError("Error al cargar los datos del proyecto"));
            }
        }).start();
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError("El nombre es obligatorio");
            return;
        }

        if (categoryCombo.getValue() == null) {
            showError("La categoría es obligatoria");
            return;
        }

        new Thread(() -> {
            try {
                String url = "/api/projects/" + projectId +
                        "?name=" + java.net.URLEncoder.encode(name, "UTF-8") +
                        "&description=" + java.net.URLEncoder.encode(
                        descriptionField.getText().trim(), "UTF-8") +
                        "&category=" + categoryCombo.getValue() +
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
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }

    private String mapStatus(String label) {
        return switch (label) {
            case "EN CURSO"    -> "IN_PROGRESS";
            case "COMPLETADO"  -> "DONE";
            case "CANCELADO"   -> "CANCELLED";
            default            -> "TODO";
        };
    }

    private String mapPriority(String label) {
        return switch (label) {
            case "BAJA"   -> "LOW";
            case "ALTA"   -> "HIGH";
            case "URGENTE"-> "URGENT";
            default       -> "MEDIUM";
        };
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
