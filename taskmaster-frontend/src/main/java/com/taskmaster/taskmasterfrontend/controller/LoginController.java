package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.IconCatalog;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.HashMap;

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
    private final ObjectMapper    objectMapper  = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Inicializa la pantalla configurando el envío del formulario
     * con la tecla Enter en los campos de usuario y contraseña.
     */
    @FXML
    private void initialize() {
        usernameField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) {handleLogin();}});
        passwordField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) {handleLogin();}});
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    /**
     * Autentica al usuario contra el backend con las credenciales introducidas.
     *
     * <p>Valida que los campos no estén vacíos, envía la petición en un hilo
     * secundario y, si el login es exitoso, almacena los datos de sesión,
     * aplica el tema del usuario y navega a la pantalla principal.</p>
     */
    @FXML
    private void handleLogin() {
        String identifier = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (identifier.isEmpty() || password.isEmpty()) {
            showError(lm.get("error.fields.required"));
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        Thread t = new Thread(() -> {
            try {
                // Spring Security espera el campo "username" aunque sea un email
                var credentials = new HashMap<String, String>();
                credentials.put("username", identifier);   // Spring Security espera este key
                credentials.put("password", password);

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().post("/api/auth/login", credentials);

                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    long userId = json.get("id").asLong();
                    String username = json.get("username").asText();
                    boolean  hasAvatar    = json.has("hasAvatar")
                            && json.get("hasAvatar").asBoolean(false);

                    // Guardamos el username real devuelto por el backend,
                    // no el identificador introducido (puede haber sido el email)
                    AppContext.getInstance().getApiService().setCredentials(username, password);
                    AppContext.getInstance().setCurrentUserId(userId);
                    AppContext.getInstance().setCurrentUsername(username);
                    AppContext.getInstance().setCurrentPassword(password);
                    AppContext.getInstance().setHasAvatar(hasAvatar);

                    if (json.has("birthDate") && !json.get("birthDate").isNull()) {
                        try {
                            LocalDate birthDate = LocalDate.parse(
                                    json.get("birthDate").asText().substring(0, 10));
                            AppContext.getInstance().setCurrentBirthDate(birthDate);
                        } catch (Exception ignored) {}
                    }

                    applyUserTheme();

                    Platform.runLater(() -> {
                        navigateToMain();
                        checkBirthday();
                    });

                } else {
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
        }, "login-request");
        t.setDaemon(true);
        t.start();
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

            String cssUrl = getAmatistaThemeUrl();
            if (cssUrl != null) scene.getStylesheets().add(cssUrl);

            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setWidth(400);
            stage.setHeight(660);
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError(lm.get("error.open.dialog"));
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Carga el tema guardado en los ajustes del backend y lo aplica.
     * Se ejecuta en el hilo secundario del login, justo antes de navegar.
     */
    private void applyUserTheme() {
        try {
            HttpResponse<String> settingsResp = AppContext.getInstance()
                    .getApiService().get("/api/settings");
            if (settingsResp.statusCode() == 200) {
                JsonNode settings  = objectMapper.readTree(settingsResp.body());
                String   themeName = settings.has("theme")
                        ? settings.get("theme").asText() : "AMATISTA";
                ThemeManager.Theme theme = ThemeManager.fromString(themeName);
                Platform.runLater(() -> ThemeManager.getInstance().applyTheme(theme));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Carga la vista principal y reemplaza la escena actual con ella,
     * aplicando el tema activo y maximizando la ventana.
     */
    private void navigateToMain() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/main-view.fxml"),
                    LanguageManager.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 900, 600);

            // Registramos el scene en ThemeManager para que aplique el CSS activo
            ThemeManager.getInstance().setMainScene(scene);

            scene.setFill(Color.web(ThemeManager.getInstance().getBgApp()));

            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(true);
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
     * Comprueba si la fecha de nacimiento del usuario coincide con el día
     * de hoy y, en ese caso, muestra un diálogo de felicitación.
     */
    private void checkBirthday() {
        LocalDate birthDate = AppContext.getInstance().getCurrentBirthDate();
        if (birthDate == null) return;
        LocalDate today = LocalDate.now();
        if (birthDate.getMonthValue() != today.getMonthValue()
                || birthDate.getDayOfMonth() != today.getDayOfMonth()) return;

        // Icono de tarta
        FontIcon cakeIcon = new FontIcon(IconCatalog.UI_BIRTHDAY);
        cakeIcon.getStyleClass().add("birthday-dialog-icon");

        // Título y cuerpo
        Label headerLabel = new Label(MessageFormat.format(
                lm.get("birthday.header"),
                AppContext.getInstance().getCurrentUsername()));
        headerLabel.getStyleClass().add("birthday-dialog-header");
        headerLabel.setWrapText(true);

        Label messageLabel = new Label(lm.get("birthday.message"));
        messageLabel.getStyleClass().add("birthday-dialog-message");
        messageLabel.setWrapText(true);

        // Botón de cierre con icono
        FontIcon btnIcon = new FontIcon(IconCatalog.ACTION_SAVE);
        Button closeButton = new Button(lm.get("birthday.button"), btnIcon);
        closeButton.getStyleClass().add("primary-button");

        // Layout
        VBox content = new VBox(12, cakeIcon, headerLabel, messageLabel, closeButton);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPadding(new javafx.geometry.Insets(24));
        content.getStyleClass().add("birthday-dialog-content");

        // Diálogo
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(lm.get("birthday.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        // Ocultamos el botón nativo de CLOSE para usar el nuestro
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setManaged(false);

        closeButton.setOnAction(e -> dialog.setResult(null));
        closeButton.setOnAction(e -> { dialog.setResult(null); dialog.close(); });

        // Aplicar el tema activo al DialogPane
        Scene mainScene = ThemeManager.getInstance().getMainScene();
        if (mainScene != null) {
            dialog.getDialogPane().getStylesheets().addAll(mainScene.getStylesheets());
        }

        dialog.showAndWait();
    }

    /**
     * Devuelve la URL externa del CSS del tema Amatista.
     * Usado como tema por defecto en las pantallas de login, registro y seguridad.
     *
     * @return URL externa del fichero CSS, o {@code null} si no se encuentra
     */
    private String getAmatistaThemeUrl() {
        var resource = getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css");
        return resource != null ? resource.toExternalForm() : null;
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
