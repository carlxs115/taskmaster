package com.taskmaster.taskmasterfrontend.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private DatePicker birthDatePicker;
    @FXML private Label errorLabel;

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
            showError("Rellena todos los campos");
            return;
        }

        if (birthDate == null) {
            showError("Selecciona tu fecha de nacimiento");
            return;
        }

        if (birthDate.isAfter(LocalDate.now().minusYears(12))) {
            showError("Debes tener al menos 12 años para registrarte");
            return;
        }

        // TODO: conectar con el backend
        System.out.println("Registro: " + username + " / " + email + " / " + birthDate);
    }

    @FXML
    private void handleGoToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml")
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
        } catch (IOException e) {
            showError("Error al cargar la pantalla de login");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
