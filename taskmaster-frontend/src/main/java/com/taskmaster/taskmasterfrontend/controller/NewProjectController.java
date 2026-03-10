package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.http.HttpResponse;
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

    // Callback que se ejecuta cuando el proyecto se crea correctamente
    // Permite notificar al MainController sin acoplamiento directo
    private Runnable onProjectCreated;

    public void setOnProjectCreated(Runnable callback) {
        this.onProjectCreated = callback;
    }

    @FXML
    private void handleCreate() {
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();

        if (name.isEmpty()) {
            showError("El nombre del proyecto es obligatorio");
            return;
        }

        new Thread(() -> {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("name", name);
                params.put("description", description);

                // Usamos postWithAuth porque este endpoint requiere autenticación
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .postWithAuthNoBody("/api/projects?name=" +
                                java.net.URLEncoder.encode(name, "UTF-8") + "&description=" +
                                java.net.URLEncoder.encode(description, "UTF-8"));

                javafx.application.Platform.runLater(() -> {
                    if (response.statusCode() == 201) {
                        // Notificamos al MainController y cerramos el diálogo
                        if (onProjectCreated != null) onProjectCreated.run();
                        closeDialog();
                    } else {
                        showError("Error al crear el proyecto");
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> showError("Error de conexión con el servidor"));
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

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
