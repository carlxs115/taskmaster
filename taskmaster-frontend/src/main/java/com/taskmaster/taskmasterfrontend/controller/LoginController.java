package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
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
 * Controlador de la pantalla de inicio de sesión.
 *
 * <p>Gestiona la autenticación del usuario contra el backend, almacena
 * los datos de sesión en {@link AppContext}, aplica el tema guardado en
 * los ajustes del usuario y navega a la pantalla principal tras un login
 * exitoso. También muestra un mensaje de felicitación si el día del login
 * coincide con el cumpleaños del usuario.</p>
 *
 * @author Carlos
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Inicializa la pantalla configurando el envío del formulario
     * con la tecla Enter en los campos de usuario y contraseña.
     */
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
     * Autentica al usuario contra el backend con las credenciales introducidas.
     *
     * <p>Valida que los campos no estén vacíos, envía la petición en un hilo
     * secundario y, si el login es exitoso, almacena los datos de sesión,
     * aplica el tema del usuario y navega a la pantalla principal.</p>
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

                    try {
                        HttpResponse<String> settingsResp = AppContext.getInstance()
                                .getApiService().get("/api/settings");
                        if (settingsResp.statusCode() == 200) {
                            JsonNode settings = mapper.readTree(settingsResp.body());
                            String themeName = settings.has("theme")
                                    ? settings.get("theme").asText()
                                    : "AMATISTA";
                            ThemeManager.Theme theme = ThemeManager.fromString(themeName);
                            Platform.runLater(() -> ThemeManager.getInstance().applyTheme(theme));
                        }
                    } catch (Exception ignored) {}

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
     * Carga la vista principal y reemplaza la escena actual con ella,
     * aplicando el tema activo y maximizando la ventana.
     */
    private void navigateToMain() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/main-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Scene scene = new Scene(loader.load(), 900, 600);

            // ── Registrar el nuevo Scene en ThemeManager y aplicar tema ──
            ThemeManager.getInstance().setMainScene(scene);

            scene.setFill(javafx.scene.paint.Color.web(
                    com.taskmaster.taskmasterfrontend.util.ThemeManager.getInstance().getBgApp()
            ));

            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.setTitle("TaskMaster");
        } catch (Exception e) {
            Platform.runLater(() -> {
                showError(lm.get("error.open.dialog"));
                loginButton.setDisable(false);
            });
        }
    }

    /**
     * Navega a la pantalla de registro manteniendo el tema Amatista.
     */
    @FXML
    private void handleGoToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/register-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Scene scene = new Scene(loader.load(), 400, 660);

            // Tema Amatista fijo para register
            String css = getClass().getResource(
                    "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css"
            ).toExternalForm();
            scene.getStylesheets().add(css);

            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setWidth(400);
            stage.setHeight(660);
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError(lm.get("error.open.dialog"));
        }
    }

    /**
     * Comprueba si la fecha de nacimiento del usuario coincide con el día
     * de hoy y, en ese caso, muestra un diálogo de felicitación.
     */
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

    /**
     * Muestra un mensaje de error en la etiqueta de error de la pantalla.
     *
     * @param message Mensaje de error a mostrar.
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
