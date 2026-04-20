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
import javafx.stage.Stage;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class SecurityController {

    @FXML private VBox accessLogContainer;
    @FXML private Label accessLogEmpty;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordErrorLabel;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", new Locale("es", "ES"));

    private final LanguageManager lm = LanguageManager.getInstance();

    @FXML
    public void initialize() {
        loadAccessLog();
    }

    private void loadAccessLog() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/access-log");
                if (response.statusCode() == 200) {
                    JsonNode entries = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderAccessLog(entries));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

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

            boolean isLogin = "LOGIN".equals(actionType);
            String icon  = isLogin ? "→" : "←";
            String label = isLogin ? lm.get("security.access.login") : lm.get("security.access.logout");
            String color = isLogin ? "#22c55e" : "#9999bb";

            LocalDateTime dt = LocalDateTime.parse(createdAt);
            String dateStr   = dt.format(FORMATTER);

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 10 20 10 20;" +
                    (i < max - 1 ? "-fx-border-color: transparent transparent #f0f0f0 transparent;" +
                            "-fx-border-width: 0 0 1 0;" : ""));

            Label iconLabel = new Label(icon);
            iconLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

            Label actionLabel = new Label(label);
            actionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e1e2e;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label dateLabel = new Label(dateStr);
            dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

            row.getChildren().addAll(iconLabel, actionLabel, spacer, dateLabel);
            accessLogContainer.getChildren().add(row);
        }
    }

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

        new Thread(() -> {
            try {
                String body = objectMapper.writeValueAsString(
                        Map.of("currentPassword", current, "newPassword", newPass));
                System.out.println(">>> body enviado: " + body);
                // PATCH /api/auth/password (no PUT, no /api/users/password)
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().patch("/api/auth/password", body);
                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        currentPasswordField.clear();
                        newPasswordField.clear();
                        confirmPasswordField.clear();
                        hideError();
                        // Actualizar contraseña en AppContext para que HTTP Basic siga funcionando
                        AppContext.getInstance().setCurrentPassword(newPass);
                        AppContext.getInstance().getApiService()
                                .setCredentials(AppContext.getInstance().getCurrentUsername(), newPass);
                        showInfo(lm.get("security.password.success"));
                    } else {
                        showError(lm.get("security.password.error.wrong"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("security.password.error.generic")));
            }
        }).start();
    }

    @FXML
    private void handleDeleteAccount() {
        // Pedir la contraseña antes de eliminar
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(lm.get("security.delete.title"));
        dialog.setHeaderText(lm.get("security.delete.header"));
        dialog.setContentText(lm.get("security.delete.prompt"));

        // Convertir el TextField en PasswordField
        PasswordField pf = new PasswordField();
        pf.setPromptText("Contraseña");
        dialog.getEditor().textProperty().bindBidirectional(pf.textProperty());

        dialog.showAndWait().ifPresent(password -> {
            if (password.isBlank()) {
                showInfo(lm.get("security.delete.empty"));
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(lm.get("security.delete.title"));
            confirm.setHeaderText(lm.get("security.delete.confirm.header"));
            confirm.setContentText(lm.get("security.delete.confirm.content"));
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    new Thread(() -> {
                        try {
                            String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8);
                            HttpResponse<String> response = AppContext.getInstance()
                                    .getApiService().delete("/api/auth/account?password=" + encodedPassword);
                            Platform.runLater(() -> {
                                if (response.statusCode() == 200 || response.statusCode() == 204) {
                                    AppContext.getInstance().logout();
                                    navigateToLogin();
                                } else {
                                    showInfo(lm.get("security.delete.error"));
                                }
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> showInfo(lm.get("security.delete.error.generic")));
                        }
                    }).start();
                }
            });
        });
    }

    private void navigateToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Stage stage = (Stage) accessLogContainer.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
            stage.setTitle("TaskMaster");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        passwordErrorLabel.setText(msg);
        passwordErrorLabel.setVisible(true);
        passwordErrorLabel.setManaged(true);
    }

    private void hideError() {
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(lm.get("security.info.title"));
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}