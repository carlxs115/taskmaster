package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
    @FXML private Label usernameValueLabel;
    @FXML private Label emailLabel;
    @FXML private Label birthDateLabel;
    @FXML private Label createdAtLabel;
    @FXML private HBox statsRow;
    @FXML private Label completionRateLabel;
    @FXML private StackPane completionBarFill;

    private Runnable onProfileUpdated;

    public void setOnProfileUpdated(Runnable callback) {
        this.onProfileUpdated = callback;
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @FXML
    public void initialize() {
        loadProfile();
        loadStats();
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

    private void loadStats() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/users/stats");
                System.out.println("Stats status: " + response.statusCode());
                System.out.println("Stats body: " + response.body());
                if (response.statusCode() == 200) {
                    JsonNode stats = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderStats(stats));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las estadísticas"));
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
            } catch (Exception ignored) {
                createdAtLabel.setText("-");
            }
        }
    }

    private void renderStats(JsonNode stats) {
        long total      = stats.get("totalTasks").asLong();
        long completed  = stats.get("completedTasks").asLong();
        long pending    = stats.get("pendingTasks").asLong();
        long inProgress = stats.get("inProgressTasks").asLong();
        long cancelled  = stats.get("cancelledTasks").asLong();
        long projects   = stats.get("totalProjects").asLong();
        int  rate       = stats.get("completionRate").asInt();

        statsRow.getChildren().setAll(
                createStatCard(String.valueOf(total),      "Total tareas",    "#7c3aed"),
                createStatCard(String.valueOf(completed),  "Completadas",     "#22c55e"),
                createStatCard(String.valueOf(pending),    "Pendientes",      "#3b82f6"),
                createStatCard(String.valueOf(inProgress), "En progreso",     "#f59e0b"),
                createStatCard(String.valueOf(cancelled),  "Canceladas",      "#e74c3c"),
                createStatCard(String.valueOf(projects),   "Proyectos",       "#ec4899")
        );
        for (javafx.scene.Node c : statsRow.getChildren())
            HBox.setHgrow(c, Priority.ALWAYS);

        // Barra de progreso
        completionRateLabel.setText(rate + "% completado");
        completionBarFill.prefWidthProperty().bind(
                completionBarFill.getParent() instanceof javafx.scene.layout.Region parent
                        ? parent.widthProperty().multiply(rate / 100.0)
                        : null
        );
        // Ancho de la barra via estilo
        completionBarFill.setStyle(
                "-fx-background-color: #22c55e; -fx-background-radius: 4px; " +
                        "-fx-pref-width: " + rate + "%;");
    }

    private VBox createStatCard(String number, String label, String color) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-padding: 14 10 14 10; " +
                "-fx-background-radius: 8px; -fx-border-color: #e8e8e8; " +
                "-fx-border-radius: 8px; -fx-border-width: 1;");
        Label num = new Label(number);
        num.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");
        lbl.setWrapText(true);
        lbl.setAlignment(Pos.CENTER);
        card.getChildren().addAll(num, lbl);
        return card;
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
