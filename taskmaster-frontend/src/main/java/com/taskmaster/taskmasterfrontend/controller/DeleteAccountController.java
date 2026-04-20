package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.net.http.HttpResponse;

public class DeleteAccountController {

    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private Runnable onAccountDeleted;
    private final LanguageManager lm = LanguageManager.getInstance();

    public void setOnAccountDeleted(Runnable callback) {
        this.onAccountDeleted = callback;
    }

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

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void closeDialog() {
        ((Stage) passwordField.getScene().getWindow()).close();
    }
}
