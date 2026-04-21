package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private DatePicker birthDatePicker;
    @FXML private Label errorLabel;

    private final LanguageManager lm = LanguageManager.getInstance();

    @FXML
    private void initialize() {
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });
        emailField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                handleRegister();
            }
        });
    }

    /**
     * Se ejecuta cuando el usuario pulsa "Crear cuenta".
     */
    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();
        LocalDate birthDate = birthDatePicker.getValue();

        // Validaciones básicas
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError(lm.get("error.fields.required"));
            return;
        }

        if (birthDate == null) {
            showError(lm.get("common.birthdate.prompt"));
            return;
        }

        if (birthDate.isAfter(LocalDate.now().minusYears(12))) {
            showError(lm.get("register.error.age"));
            return;
        }

        new Thread(() -> {
            try {
                var body = new java.util.HashMap<String, Object>();
                body.put("username", username);
                body.put("email", email);
                body.put("password", password);
                body.put("birthDate", birthDate.toString());

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .post("/api/auth/register", body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 201) {
                        handleGoToLogin();
                    } else {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode json = mapper.readTree(response.body());
                            String msg = json.has("message")
                                    ? json.get("message").asText()
                                    : lm.get("register.error.generic");
                            showError(msg);
                        } catch (Exception ex) {
                            showError(lm.get("register.error.generic"));
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();

    }

    @FXML
    private void handleGoToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
        } catch (IOException e) {
            showError(lm.get("error.open.dialog"));
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
