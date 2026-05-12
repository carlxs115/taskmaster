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

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;

/**
 * Controlador de la pantalla de registro de nuevos usuarios.
 *
 * <p>Valida los datos introducidos (campos obligatorios, edad mínima de 12 años)
 * y envía la solicitud de registro al backend. Si el registro es exitoso,
 * redirige automáticamente a la pantalla de login.</p>
 *
 * @author Carlos
 */
public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private DatePicker birthDatePicker;
    @FXML private Label errorLabel;

    private final LanguageManager lm = LanguageManager.getInstance();
    private final ObjectMapper    objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Inicializa la pantalla configurando el envío del formulario
     * con la tecla Enter en los campos de texto.
     */
    @FXML
    private void initialize() {
        usernameField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) {handleRegister();}});
        emailField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) {handleRegister();}});
        passwordField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) {handleRegister();}});
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    /**
     * Valida el formulario y envía la solicitud de registro al backend.
     *
     * <p>Comprueba que todos los campos estén rellenos, que se haya
     * seleccionado una fecha de nacimiento y que el usuario tenga al menos
     * 12 años. Si el registro es exitoso, navega a la pantalla de login.</p>
     */
    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();
        LocalDate birthDate = birthDatePicker.getValue();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError(lm.get("error.fields.required"));
            return;
        }

        if (birthDate == null) {
            showError(lm.get("common.birthdate.prompt"));
            return;
        }

        // Validamos edad mínima de 12 años
        if (birthDate.isAfter(LocalDate.now().minusYears(12))) {
            showError(lm.get("register.error.age"));
            return;
        }

        Thread t = new Thread(() -> {
            try {
                var body = new HashMap<String, Object>();
                body.put("username", username);
                body.put("email", email);
                body.put("password", password);
                body.put("birthDate", birthDate.toString());

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().post("/api/auth/register", body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 201) {
                        // Registro exitoso: navegamos al login
                        handleGoToLogin();
                    } else {
                        // Intentamos mostrar el mensaje de error del backend
                        try {
                            JsonNode json = objectMapper.readTree(response.body());
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
        }, "register-request");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Navega a la pantalla de login manteniendo el tema Amatista.
     */
    @FXML
    private void handleGoToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml"),
                    LanguageManager.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 400, 520);
            String cssUrl = getAmatistaThemeUrl();
            if (cssUrl != null) scene.getStylesheets().add(cssUrl);

            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setWidth(400);
            stage.setHeight(520);
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError(lm.get("error.open.dialog"));
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
     * Muestra un mensaje de error en la etiqueta de error de la pantalla.
     *
     * @param message Mensaje de error a mostrar.
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}