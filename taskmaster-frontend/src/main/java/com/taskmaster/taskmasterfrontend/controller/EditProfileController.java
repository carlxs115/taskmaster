package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
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

/**
 * Controlador del diálogo de edición del perfil de usuario.
 *
 * <p>Carga los datos actuales del perfil desde el backend al inicializarse
 * y permite modificar el nombre de usuario, el correo electrónico y la fecha
 * de nacimiento. Tras guardar correctamente, actualiza las credenciales en
 * {@link AppContext} y ejecuta el callback registrado.</p>
 *
 * @author Carlos
 */
public class EditProfileController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private DatePicker birthDatePicker;
    @FXML private Label errorLabel;

    private Runnable onProfileUpdated;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Registra el callback que se ejecutará tras actualizar el perfil correctamente.
     *
     * @param callback Acción a ejecutar al completar la actualización.
     */
    public void setOnProfileUpdated(Runnable callback) {
        this.onProfileUpdated = callback;
    }

    /**
     * Inicializa el diálogo cargando los datos actuales del perfil.
     */
    @FXML
    public void initialize() {
        loadProfile();
    }

    /**
     * Obtiene el perfil del usuario autenticado desde el backend y rellena
     * los campos del formulario con los datos recibidos.
     */
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
                Platform.runLater(() -> showError(lm.get("edit.profile.error.load")));
            }
        }).start();
    }

    /**
     * Valida los campos del formulario y envía los datos actualizados al backend.
     * Si la operación es exitosa, actualiza el username en {@link AppContext},
     * refresca las credenciales del servicio API y cierra el diálogo.
     */
    @FXML
    private void handleSave() {
        String username  = usernameField.getText().trim();
        String email     = emailField.getText().trim();
        LocalDate birthDate = birthDatePicker.getValue();

        if (username.isEmpty() || email.isEmpty() || birthDate == null) {
            showError(lm.get("edit.profile.error.fields"));
            return;
        }

        // Validación edad mínima 12 años
        if (birthDate.isAfter(LocalDate.now().minusYears(12))) {
            showError(lm.get("edit.profile.error.age"));
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
                            showError(msg.isEmpty() ? lm.get("common.error.save") : msg);
                        } catch (Exception ignored) {
                            showError(lm.get("common.error.save"));
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();
    }

    /**
     * Cierra el diálogo sin guardar cambios.
     */
    @FXML
    private void handleCancel() {
        closeDialog();
    }

    /**
     * Muestra un mensaje de error en la etiqueta de error del formulario.
     *
     * @param msg Mensaje de error a mostrar.
     */
    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /**
     * Oculta la etiqueta de error del formulario.
     */
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /**
     * Cierra el diálogo actual.
     */
    private void closeDialog() {
        ((Stage) usernameField.getScene().getWindow()).close();
    }
}
