package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.net.http.HttpResponse;

/**
 * Controlador del diálogo de eliminación de cuenta de usuario.
 *
 * <p>Solicita la contraseña actual como confirmación antes de proceder.
 * Si el backend confirma la eliminación, ejecuta el callback registrado
 * para que el controlador padre pueda redirigir al usuario a la pantalla de login.</p>
 *
 * @author Carlos
 */
public class DeleteAccountController {

    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private Runnable onAccountDeleted;
    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Registra el callback que se ejecutará tras eliminar la cuenta correctamente.
     *
     * @param callback Acción a ejecutar al completar la eliminación.
     */
    public void setOnAccountDeleted(Runnable callback) {
        this.onAccountDeleted = callback;
    }

    /**
     * Valida que el campo de contraseña no esté vacío y envía la solicitud
     * de eliminación de cuenta al backend. Si la operación es exitosa,
     * cierra el diálogo y ejecuta el callback registrado.
     */
    @FXML
    private void handleDelete() {
        String password = passwordField.getText();

        if (password.isEmpty()) {
            showError(lm.get("security.delete.empty"));
            return;
        }

        hideError();

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .delete("/api/auth/account?password=" + password);

                Platform.runLater(() -> {
                    if (response.statusCode() == 204) {
                        closeDialog();
                        if (onAccountDeleted != null) onAccountDeleted.run();
                    } else {
                        String msg = response.body();
                        showError(msg != null && !msg.isEmpty()
                                ? msg : lm.get("security.delete.error"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();
    }

    /**
     * Cierra el diálogo sin eliminar la cuenta.
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
        ((Stage) passwordField.getScene().getWindow()).close();
    }
}
