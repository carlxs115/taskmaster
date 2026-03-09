package com.taskmaster.taskmasterfrontend.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * LOGINCONTROLLER
 *
 * Controlador de la pantalla de login.
 * @FXML -> vincula los elementos del FXML con variables Java.
 *         El nombre de la variable debe coincidir exactamente
 *         con el fx:id del elemento en el FXML.
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    /**
     * Se ejecuta cuando el usuario pulsa "Iniciar sesión".
     * Por ahora validamos que los campos no estén vacíos.
     * Más adelante conectaremos con el backend.
     */
    @FXML
    private void handleLogin(){
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Rellena todos los campos");
            return;
        }

        // TODO: conectar con el backend
        System.out.println("Login: " + username);
    }

    /**
     * Se ejecuta cuando el usuario pulsa "Regístrate".
     * Navega a la pantalla de registro.
     */
    @FXML
    private void handleGoToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/register-view.fxml")
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 600));
        } catch (IOException e) {
            showError("Error al cargar la pantalla de registro");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
