package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Controlador de la pantalla de seguridad.
 *
 * <p>Muestra el historial de accesos (login/logout/cambio de contraseña) del
 * usuario y permite cambiar la contraseña de la cuenta. También gestiona la
 * eliminación permanente de la cuenta tras doble confirmación, redirigiendo
 * al login si la operación es exitosa.</p>
 *
 * @author Carlos
 */
public class SecurityController {

    private static final Logger log = LoggerFactory.getLogger(SecurityController.class);

    @FXML private VBox accessLogContainer;
    @FXML private Label accessLogEmpty;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordErrorLabel;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Inicializa la pantalla cargando el historial de accesos del usuario.
     */
    @FXML
    public void initialize() {
        loadAccessLog();
    }

    // -------------------------------------------------------------------------
    // Historial de accesos
    // -------------------------------------------------------------------------

    /**
     * Obtiene el historial de accesos del backend y lo renderiza en la vista.
     */
    private void loadAccessLog() {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/access-log");
                if (response.statusCode() == 200) {
                    JsonNode entries = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderAccessLog(entries));
                }
            } catch (Exception e) {
                log.error("Error al cargar el historial de accesos: {}", e.getMessage());
            }
        }, "security-load-access-log");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Renderiza las últimas 10 entradas del historial de accesos con icono,
     * etiqueta de acción y fecha formateada.
     *
     * @param entries array JSON con las entradas del historial
     */
    private void renderAccessLog(JsonNode entries) {
        accessLogContainer.getChildren().clear();
        if (entries.isEmpty()) {
            accessLogContainer.getChildren().add(accessLogEmpty);
            return;
        }

        int max = Math.min(entries.size(), 10);
        for (int i = 0; i < max; i++) {
            JsonNode entry = entries.get(i);
            String actionType = entry.get("actionType").asText();
            String createdAt  = entry.get("createdAt").asText();

            boolean isLogin          = "LOGIN".equals(actionType);
            boolean isPasswordChange = "PASSWORD_CHANGED".equals(actionType);

            String iconLiteral = isPasswordChange ? "fas-key"
                    : isLogin          ? "fas-sign-in-alt"
                    : "fas-sign-out-alt";

            String label = isPasswordChange ? lm.get("common.password.changed")
                    : isLogin          ? lm.get("security.access.login")
                    : lm.get("security.access.logout");

            String color = isPasswordChange ? "#f59e0b"
                    : isLogin          ? "#22c55e"
                    : "#9999bb";

            LocalDateTime dt = LocalDateTime.parse(createdAt);
            String dateStr = dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", lm.getCurrentLocale()));

            FontIcon iconNode = new FontIcon(iconLiteral);
            iconNode.setIconSize(14);
            iconNode.setIconColor(Color.web(color));

            Label actionLabel = new Label(label);
            actionLabel.getStyleClass().add("profile-field-value");

            Label dateLabel = new Label(dateStr);
            dateLabel.getStyleClass().add("profile-field-label");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(12, iconNode, actionLabel, spacer, dateLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("security-access-row");

            accessLogContainer.getChildren().add(row);
        }
    }

    // -------------------------------------------------------------------------
    // Cambio de contraseña
    // -------------------------------------------------------------------------

    /**
     * Valida los campos del formulario de cambio de contraseña y, si son
     * correctos, envía la solicitud al backend. Si la operación es exitosa,
     * actualiza las credenciales en {@link AppContext}.
     */
    @FXML
    private void handleChangePassword() {
        String current = currentPasswordField.getText().trim();
        String newPass = newPasswordField.getText().trim();
        String confirm = confirmPasswordField.getText().trim();

        if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            showError(lm.get("security.password.error.fields"));
            return;
        }
        if (!newPass.equals(confirm)) {
            showError(lm.get("security.password.error.match"));
            return;
        }
        if (newPass.length() < 6) {
            showError(lm.get("security.password.error.length"));
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                String body = objectMapper.writeValueAsString(
                        Map.of("currentPassword", current, "newPassword", newPass));

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().patch("/api/auth/password", body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        currentPasswordField.clear();
                        newPasswordField.clear();
                        confirmPasswordField.clear();
                        hideError();

                        // Actualizamos la contraseña en AppContext para que
                        // HTTP Basic siga funcionando con la nueva contraseña
                        AppContext.getInstance().setCurrentPassword(newPass);
                        AppContext.getInstance().getApiService().setCredentials(
                                AppContext.getInstance().getCurrentUsername(), newPass);

                        showInfo(lm.get("security.password.success"));
                        loadAccessLog();
                    } else {
                        showError(lm.get("security.password.error.wrong"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("security.password.error.generic")));
            }
        }, "security-change-password");
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Eliminación de cuenta
    // -------------------------------------------------------------------------

    /**
     * Solicita la contraseña actual mediante un diálogo, pide confirmación
     * adicional y, si el usuario acepta, elimina la cuenta en el backend.
     * Si la eliminación es exitosa, cierra la sesión y navega al login.
     */
    @FXML
    private void handleDeleteAccount() {
        PasswordField pf = new PasswordField();
        pf.setPromptText(lm.get("common.password"));

        VBox content = new VBox(8, new Label(lm.get("security.delete.header")), pf);

        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle(lm.get("common.delete.account"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(pf::requestFocus);

        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK))
                .setText(lm.get("common.confirm"));
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL))
                .setText(lm.get("common.cancel"));

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;

            String password = pf.getText();
            if (password.isBlank()) {
                showInfo(lm.get("security.delete.empty"));
                return;
            }

            // Segunda confirmación antes de la operación irreversible
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(lm.get("common.delete.account"));
            confirm.setHeaderText(lm.get("security.delete.confirm.header"));
            confirm.setContentText(lm.get("security.delete.confirm.content"));

            ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK))
                    .setText(lm.get("common.confirm"));
            ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL))
                    .setText(lm.get("common.cancel"));

            confirm.showAndWait().ifPresent(r -> {
                if (r != ButtonType.OK) return;
                Stage stage = (Stage) accessLogContainer.getScene().getWindow();
                deleteAccountAsync(password, stage);
            });
        });
    }

    /**
     * Ejecuta la eliminación de cuenta en un hilo secundario.
     *
     * @param password contraseña confirmada por el usuario
     * @param stage    ventana principal para navegar al login tras el borrado
     */
    private void deleteAccountAsync(String password, Stage stage) {
        Thread thread = new Thread(() -> {
            try {
                String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8);
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .delete("/api/auth/account?password=" + encodedPassword);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200 || response.statusCode() == 204) {
                        AppContext.getInstance().logout();
                        navigateToLogin(stage);
                    } else {
                        showInfo(lm.get("security.delete.error"));
                    }
                });
            } catch (Exception e) {
                log.error("Error al eliminar la cuenta: {}", e.getMessage());
                Platform.runLater(() -> showInfo(lm.get("security.delete.error.generic")));
            }
        }, "security-delete-account");
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * Carga la vista de login y reemplaza la escena actual con ella
     * tras la eliminación exitosa de la cuenta.
     *
     * @param stage ventana principal de la aplicación
     */
    private void navigateToLogin(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Scene scene = new Scene(loader.load(), 400, 520);
            String cssUrl = getAmatistaThemeUrl();
            if (cssUrl != null) scene.getStylesheets().add(cssUrl);

            stage.setScene(scene);
            stage.setWidth(400);
            stage.setHeight(520);
            stage.setMinWidth(400);
            stage.setMinHeight(520);
            stage.setResizable(false);
            stage.centerOnScreen();
            stage.setTitle("TaskMaster");
        } catch (Exception e) {
            log.error("Error al navegar al login: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

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
     * Muestra un mensaje de error en la etiqueta de error del formulario
     * de cambio de contraseña.
     *
     * @param msg Mensaje de error a mostrar.
     */
    private void showError(String msg) {
        passwordErrorLabel.setText(msg);
        passwordErrorLabel.setVisible(true);
        passwordErrorLabel.setManaged(true);
    }

    /**
     * Oculta la etiqueta de error del formulario de cambio de contraseña.
     */
    private void hideError() {
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
    }

    /**
     * Muestra un diálogo de información con el mensaje indicado.
     *
     * @param msg Mensaje a mostrar.
     */
    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(lm.get("security.info.title"));
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}