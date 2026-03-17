package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class EditProfileController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private DatePicker birthDatePicker;
    @FXML private Label errorLabel;

    private Runnable onProfileUpdated;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public void setOnProfileUpdated(Runnable callback) {
        this.onProfileUpdated = callback;
    }

    @FXML
    public void initialize() {
        loadProfile();
    }

    private void loadProfile() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/auth/profile");
                if (response.statusCode() == 200) {
                    JsonNode user = objectMapper.readTree(response.body());
                    Platform.runLater(() -> {
                        usernameField.setText(user.get("username").asText());
                        emailField.setText(user.get("email").asText());
                        if (user.has("birthDate") && !user.get("birthDate").isNull()) {
                            try {
                                LocalDate date = LocalDate.parse(
                                        user.get("birthDate").asText().substring(0, 10));
                                birthDatePicker.setValue(date);
                            } catch (Exception ignored) {}
                        }
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("No se pudieron cargar los datos del perfil"));
            }
        }).start();
    }

    @FXML
    private void handleSave() {
        String username  = usernameField.getText().trim();
        String email     = emailField.getText().trim();
        LocalDate birthDate = birthDatePicker.getValue();

        if (username.isEmpty() || email.isEmpty() || birthDate == null) {
            showError("Todos los campos son obligatorios");
            return;
        }

        // Validación edad mínima 12 años
        if (birthDate.isAfter(LocalDate.now().minusYears(12))) {
            showError("Debes tener al menos 12 años");
            return;
        }

        hideError();

        Map<String, Object> body = Map.of(
                "username",  username,
                "email",     email,
                "birthDate", birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().put("/api/auth/profile", body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        // Actualizamos el username en AppContext si ha cambiado
                        try {
                            JsonNode user = objectMapper.readTree(response.body());
                            String newUsername = user.get("username").asText();
                            AppContext.getInstance().setCurrentUsername(newUsername);

                            // Actualizar credenciales con el nuevo username
                            AppContext.getInstance().getApiService().setCredentials(
                                    newUsername,
                                    AppContext.getInstance().getCurrentPassword()
                            );
                        } catch (Exception ignored) {}

                        if (onProfileUpdated != null) onProfileUpdated.run();
                        closeDialog();
                    } else {
                        try {
                            String msg = response.body();
                            showError(msg.isEmpty() ? "Error al guardar los cambios" : msg);
                        } catch (Exception ignored) {
                            showError("Error al guardar los cambios");
                        }
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
        ((Stage) usernameField.getScene().getWindow()).close();
    }
}
