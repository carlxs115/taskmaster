package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;

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

    private final LanguageManager lm = LanguageManager.getInstance();

    @FXML
    private void initialize() {
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });
    }

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
            showError(lm.get("error.fields.required"));
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
                    AppContext.getInstance().setCurrentPassword(password);

                    // Guardar si el usuario tiene avatar (para decidir si pedimos la imagen después)
                    AppContext.getInstance().setHasAvatar(
                            jsonNode.has("hasAvatar") && jsonNode.get("hasAvatar").asBoolean(false));

                    // Guardar birthDate y comprobar cumpleaños
                    if (jsonNode.has("birthDate") && !jsonNode.get("birthDate").isNull()) {
                        try {
                            LocalDate birthDate = LocalDate.parse(
                                    jsonNode.get("birthDate").asText().substring(0, 10));
                            AppContext.getInstance().setCurrentBirthDate(birthDate);
                        } catch (Exception ignored) {}
                    }

                    // Navegamos a la pantalla principal en el hilo de JavaFX
                    Platform.runLater(() -> {
                        navigateToMain();
                        checkBirthday();
                    });
                } else {
                    // Login fallido — mostramos error
                    Platform.runLater(() -> {
                        showError(lm.get("login.error.credentials"));
                        loginButton.setDisable(false);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError(lm.get("error.connection"));
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
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/main-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 900, 600));
            stage.setTitle("TaskMaster");
        } catch (Exception e) {
            Platform.runLater(() -> {
                showError(lm.get("error.open.dialog"));
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
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/register-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 600));
        } catch (IOException e) {
            showError(lm.get("error.open.dialog"));
        }
    }

    private void checkBirthday() {
        LocalDate birthDate = AppContext.getInstance().getCurrentBirthDate();

        if (birthDate == null) {
            return;
        }
        LocalDate today = LocalDate.now();

        if (birthDate.getMonthValue() == today.getMonthValue()
                && birthDate.getDayOfMonth() == today.getDayOfMonth()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(lm.get("birthday.title"));
            alert.setHeaderText(java.text.MessageFormat.format(
                    lm.get("birthday.header"),
                    AppContext.getInstance().getCurrentUsername()));
            alert.setContentText(lm.get("birthday.message"));
            ButtonType btn = new ButtonType(lm.get("birthday.button"));
            alert.getButtonTypes().setAll(btn);
            alert.showAndWait();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
