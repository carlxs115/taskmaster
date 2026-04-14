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
import javafx.scene.control.Separator;
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
import java.util.List;
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
    @FXML private VBox activityLogContainer;
    @FXML private Label activityLogEmpty;

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
        loadActivityLog();
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

    private void loadActivityLog() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/activity-log");
                if (response.statusCode() == 200) {
                    JsonNode entries = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderActivityLog(entries));
                }
            } catch (Exception e) {
                e.printStackTrace();
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

    private void renderActivityLog(JsonNode entries) {
        activityLogContainer.getChildren().clear();

        if (entries.isEmpty()) {
            activityLogContainer.getChildren().add(activityLogEmpty);
            return;
        }

        // Agrupar por fecha
        LocalDate today    = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Construir grupos en orden: Hoy, Ayer, Esta semana, Anteriores
        java.util.LinkedHashMap<String, List<JsonNode>> groups = new java.util.LinkedHashMap<>();

        for (JsonNode entry : entries) {
            LocalDate date = LocalDateTime.parse(entry.get("createdAt").asText()).toLocalDate();
            String group;
            if (date.equals(today))          group = "Hoy";
            else if (date.equals(yesterday)) group = "Ayer";
            else if (!date.isBefore(today.minusDays(7))) group = "Esta semana";
            else                             group = "Anteriores";

            groups.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(entry);
        }

        boolean firstGroup = true;
        for (java.util.Map.Entry<String, List<JsonNode>> group : groups.entrySet()) {
            // Separador entre grupos
            if (!firstGroup) {
                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: #f0f0f0;");
                activityLogContainer.getChildren().add(sep);
            }
            firstGroup = false;

            // Cabecera del grupo
            Label groupLabel = new Label(group.getKey());
            groupLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #888888; -fx-padding: 10 20 4 20;");
            activityLogContainer.getChildren().add(groupLabel);

            // Entradas del grupo
            List<JsonNode> groupEntries = group.getValue();
            for (int i = 0; i < groupEntries.size(); i++) {
                JsonNode entry = groupEntries.get(i);
                String actionType = entry.get("actionType").asText();
                String entityName = entry.has("entityName") && !entry.get("entityName").isNull()
                        ? entry.get("entityName").asText() : "";
                String oldValue   = entry.has("oldValue") && !entry.get("oldValue").isNull()
                        ? entry.get("oldValue").asText() : null;
                String newValue   = entry.has("newValue") && !entry.get("newValue").isNull()
                        ? entry.get("newValue").asText() : null;

                String icon  = getActivityIcon(actionType);
                String color = getActivityColor(actionType);
                String desc  = getActivityDescription(actionType, entityName, oldValue, newValue);

                LocalDateTime dt = LocalDateTime.parse(entry.get("createdAt").asText());
                String timeStr   = dt.format(DateTimeFormatter.ofPattern("HH:mm"));

                HBox row = new HBox(12);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                boolean isLast = i == groupEntries.size() - 1;
                row.setStyle("-fx-padding: 9 20 9 20;" +
                        (!isLast ? "-fx-border-color: transparent transparent #f5f5f5 transparent;" +
                                "-fx-border-width: 0 0 1 0;" : ""));

                Label iconLabel = new Label(icon);
                iconLabel.setStyle("-fx-font-size: 13px; -fx-min-width: 20px; -fx-text-fill: " + color + ";");

                Label descLabel = new Label(desc);
                descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e1e2e;");
                descLabel.setWrapText(true);
                HBox.setHgrow(descLabel, javafx.scene.layout.Priority.ALWAYS);

                Label timeLabel = new Label(timeStr);
                timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaaaaa;");

                row.getChildren().addAll(iconLabel, descLabel, timeLabel);
                activityLogContainer.getChildren().add(row);
            }
        }
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
                loadActivityLog();
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

    private String getActivityIcon(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED", "SUBTASK_CREATED"      -> "✅";
            case "TASK_EDITED", "SUBTASK_EDITED"        -> "✏️";
            case "TASK_DELETED", "SUBTASK_DELETED"      -> "🗑";
            case "TASK_PERMANENTLY_DELETED"             -> "❌";
            case "TASK_RESTORED"                        -> "↩️";
            case "TASK_STATUS_CHANGED"                  -> "🔄";
            case "PROJECT_CREATED"                      -> "📁";
            case "PROJECT_EDITED"                       -> "✏️";
            case "PROJECT_DELETED"                      -> "🗑";
            case "PROJECT_PERMANENTLY_DELETED"          -> "❌";
            case "PROJECT_RESTORED"                     -> "↩️";
            case "PROJECT_STATUS_CHANGED"               -> "🔄";
            case "PROFILE_UPDATED"                      -> "👤";
            case "PASSWORD_CHANGED"                     -> "🔒";
            default                                     -> "•";
        };
    }

    private String getActivityColor(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED", "SUBTASK_CREATED", "PROJECT_CREATED" -> "#22c55e";
            case "TASK_DELETED", "SUBTASK_DELETED", "PROJECT_DELETED" -> "#f59e0b";
            case "TASK_PERMANENTLY_DELETED", "PROJECT_PERMANENTLY_DELETED" -> "#e74c3c";
            case "TASK_RESTORED", "PROJECT_RESTORED" -> "#3b82f6";
            case "TASK_STATUS_CHANGED", "PROJECT_STATUS_CHANGED" -> "#7c3aed";
            default -> "#888888";
        };
    }

    private String getActivityDescription(String actionType, String entityName,
                                          String oldValue, String newValue) {
        String name = entityName.isEmpty() ? "" : " \"" + entityName + "\"";
        return switch (actionType) {
            case "TASK_CREATED"               -> "Tarea creada" + name;
            case "TASK_EDITED"                -> "Tarea editada" + name;
            case "TASK_DELETED"               -> "Tarea enviada a la papelera" + name;
            case "TASK_PERMANENTLY_DELETED"   -> "Tarea eliminada permanentemente" + name;
            case "TASK_RESTORED"              -> "Tarea restaurada" + name;
            case "TASK_STATUS_CHANGED"        -> "Tarea" + name + " → " + translateStatus(newValue);
            case "SUBTASK_CREATED"            -> "Subtarea creada" + name;
            case "SUBTASK_EDITED"             -> "Subtarea editada" + name;
            case "SUBTASK_DELETED"            -> "Subtarea eliminada" + name;
            case "PROJECT_CREATED"            -> "Proyecto creado" + name;
            case "PROJECT_EDITED"             -> "Proyecto editado" + name;
            case "PROJECT_DELETED"            -> "Proyecto enviado a la papelera" + name;
            case "PROJECT_PERMANENTLY_DELETED"-> "Proyecto eliminado permanentemente" + name;
            case "PROJECT_RESTORED"           -> "Proyecto restaurado" + name;
            case "PROJECT_STATUS_CHANGED"     -> "Proyecto" + name + " · " +
                    translateStatus(oldValue) + " → " + translateStatus(newValue);
            case "PROFILE_UPDATED"            -> "Perfil actualizado";
            case "PASSWORD_CHANGED"           -> "Contraseña cambiada";
            default                           -> actionType;
        };
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "TODO"        -> "Pendiente";
            case "IN_PROGRESS" -> "En progreso";
            case "DONE"        -> "Completada";
            case "CANCELLED"   -> "Cancelada";
            default            -> status;
        };
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
