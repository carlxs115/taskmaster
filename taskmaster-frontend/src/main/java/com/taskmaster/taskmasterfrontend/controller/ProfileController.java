package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ProfileController {

    @FXML private Label avatarLabel;
    @FXML private Label usernameLabel;
    @FXML private Label memberSinceLabel;
    @FXML private Label usernameValueLabel;
    @FXML private Label emailLabel;
    @FXML private Label birthDateLabel;
    @FXML private Label createdAtLabel;

    private Runnable onProfileUpdated;

    public void setOnProfileUpdated(Runnable callback) {
        this.onProfileUpdated = callback;
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @FXML
    public void initialize() {
        loadProfile();
    }

    private void loadProfile() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/auth/profile");
                if (response.statusCode() == 200) {
                    JsonNode user = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderProfile(user));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudo cargar el perfil"));
            }
        }).start();
    }

    private void renderProfile(JsonNode user) {
        String username = user.get("username").asText();
        String email = user.get("email").asText();

        // Avatar con iniciales
        String initials = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.toUpperCase();
        avatarLabel.setText(initials);
        usernameLabel.setText(username);
        usernameValueLabel.setText(username);
        emailLabel.setText(email);

        // Fecha de nacimiento
        if (user.has("birthDate") && !user.get("birthDate").isNull()) {
            try {
                LocalDate bd = LocalDate.parse(user.get("birthDate").asText().substring(0, 10));
                birthDateLabel.setText(bd.format(
                        DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"))));
            } catch (Exception ignored) {
                birthDateLabel.setText("-");
            }
        }

        // Fecha de registro
        if (user.has("createdAt") && !user.get("createdAt").isNull()) {
            try {
                LocalDateTime ca = LocalDateTime.parse(user.get("createdAt").asText());
                createdAtLabel.setText(ca.format(
                        DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"))));
                memberSinceLabel.setText("Miembro desde " + ca.format(
                        DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES"))));
            } catch (Exception ignored) {
                createdAtLabel.setText("-");
            }
        }
    }

    @FXML
    private void handleEditProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-profile-dialog.fxml"));
            VBox root = loader.load();
            EditProfileController controller = loader.getController();
            controller.setOnProfileUpdated(() -> {
                loadProfile();
                if (onProfileUpdated != null) {
                    onProfileUpdated.run();
                }
            });

            Stage dialog = new Stage();
            dialog.setTitle("Editar perfil");
            dialog.setScene(new Scene(root, 400, 380));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (Exception e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
