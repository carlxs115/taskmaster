package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.http.HttpResponse;

/**
 * LOGINCONTROLLER
 *
 * Controlador de la pantalla de login.
 * Conecta con el backend para autenticar al usuario.
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

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

        // Desactivamos el botón para evitar doble click
        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        // Petición en hilo separado para no bloquear la UI
        new Thread(() -> {
            try {
                // Creamos el objeto con las credenciales
                var credentials = new java.util.HashMap<String, String>();
                credentials.put("username", username);
                credentials.put("password", password);

                // Hacemos la petición POST al backend
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .post("/api/auth/login", credentials);

                if (response.statusCode() == 200) {
                    // Login exitoso - parseamos la respuesta
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(response.body());

                    Long userId = jsonNode.get("id").asLong();
                    String returnedUsername = jsonNode.get("username").asText();

                    // Guardamos las credenciales y datos del usuario en AppContext
                    AppContext.getInstance().getApiService().setCredentials(username, password);
                    AppContext.getInstance().setCurrentUserId(userId);
                    AppContext.getInstance().setCurrentUsername(returnedUsername);

                    // Navegamos a la pantalla principal en el hilo de JavaFX
                    Platform.runLater(() -> navigateToMain());
                } else {
                    // Login fallido — mostramos error
                    Platform.runLater(() -> {
                        showError("Usuario o contraseña incorrectos");
                        loginButton.setDisable(false);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Error de conexión con el servidor");
                    loginButton.setDisable(false);
                });
            }
        }).start();

        System.out.println("Login: " + username);
    }

    /**
     * Navega a la pantalla principal tras login exitoso.
     */
    private void navigateToMain() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/main-view.fxml")
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 900, 600));
            stage.setTitle("TaskMaster");
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                showError("Error al cargar la pantalla principal" + e.getMessage());
                loginButton.setDisable(false);
            });
        }
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
