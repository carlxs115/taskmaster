package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.net.http.HttpResponse;
import java.util.Map;

public class ChangePasswordController {

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML
    private void handleSave() {
        String current = currentPasswordField.getText();
        String newPass  = newPasswordField.getText();
        String confirm  = confirmPasswordField.getText();

        if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            showError("Todos los campos son obligatorios");
            return;
        }
        if (!newPass.equals(confirm)) {
            showError("Las contraseñas nuevas no coinciden");
            return;
        }
        if (newPass.length() < 6) {
            showError("La nueva contraseña debe tener al menos 6 caracteres");
            return;
        }

        hideError();

        Map<String, String> body = Map.of(
                "currentPassword", current,
                "newPassword",     newPass
        );

        new Thread(() -> {
            try {
                String json = objectMapper.writeValueAsString(body);
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().patch("/api/auth/password", json);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        closeDialog();
                    } else {
                        String msg = response.body();
                        showError(msg != null && !msg.isEmpty()
                                ? msg : "Error al cambiar la contraseña");
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
        ((Stage) currentPasswordField.getScene().getWindow()).close();
    }
}
