package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.AvatarView;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ProfileController {

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
    @FXML private StackPane avatarContainer;
    private AvatarView avatarView;

    private Runnable onProfileUpdated;

    public void setOnProfileUpdated(Runnable callback) {
        this.onProfileUpdated = callback;
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    @FXML
    public void initialize() {
        setupAvatar();
        loadProfile();
        loadStats();
        loadActivityLog();
    }

    /**
     * Configura el avatar: lo crea, lo carga y le añade un icono de cámara
     * superpuesto que al pulsarlo abre un menú con "Cambiar foto" / "Eliminar foto".
     */
    private void setupAvatar() {
        avatarView = new AvatarView(80);
        avatarView.loadForCurrentUser();

        // Icono de cámara sobre el avatar (esquina inferior derecha)
        Label cameraIcon = new Label("📷");
        cameraIcon.setStyle("-fx-background-color: #7c3aed; -fx-text-fill: white; " +
                "-fx-font-size: 12px; -fx-min-width: 26px; -fx-min-height: 26px; " +
                "-fx-max-width: 26px; -fx-max-height: 26px; -fx-alignment: CENTER; " +
                "-fx-background-radius: 13px; -fx-border-color: white; " +
                "-fx-border-width: 2; -fx-border-radius: 13px; -fx-cursor: hand;");
        StackPane.setAlignment(cameraIcon, Pos.BOTTOM_RIGHT);
        cameraIcon.setOnMouseClicked(e -> showAvatarMenu(cameraIcon));

        avatarContainer.getChildren().setAll(avatarView, cameraIcon);
        avatarContainer.setCursor(Cursor.DEFAULT);
    }

    /** Menú contextual con opciones de cambiar o eliminar la foto. */
    private void showAvatarMenu(javafx.scene.Node anchor) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        menu.setStyle("-fx-background-color: white; -fx-border-color: #e8e8e8; " +
                "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

        javafx.scene.control.MenuItem change = new javafx.scene.control.MenuItem(lm.get("profile.avatar.change"));
        change.setStyle("-fx-font-size: 13px; -fx-padding: 6 16 6 16;");
        change.setOnAction(e -> handleChangePhoto());
        menu.getItems().add(change);

        // "Eliminar foto" solo si el usuario tiene foto actualmente
        if (AppContext.getInstance().hasAvatar()) {
            javafx.scene.control.MenuItem remove = new javafx.scene.control.MenuItem(lm.get("profile.avatar.remove"));
            remove.setStyle("-fx-font-size: 13px; -fx-padding: 6 16 6 16; -fx-text-fill: #e74c3c;");
            remove.setOnAction(e -> handleRemovePhoto());
            menu.getItems().add(remove);
        }

        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    /** Abre el FileChooser, luego el diálogo de recorte, y sube el resultado. */
    private void handleChangePhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(lm.get("profile.avatar.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));

        File selected = fileChooser.showOpenDialog(avatarContainer.getScene().getWindow());
        if (selected == null) return;

        // Validación de tamaño local (2 MB) antes de abrir el diálogo
        if (selected.length() > 2 * 1024 * 1024) {
            showAlert(lm.get("error.title"), lm.get("profile.avatar.too.large"));
            return;
        }

        try (FileInputStream fis = new FileInputStream(selected)) {
            Image image = new Image(fis);
            if (image.isError()) {
                showAlert(lm.get("error.title"), lm.get("profile.avatar.error.read"));
                return;
            }
            openCropDialog(image);
        } catch (Exception e) {
            showAlert(lm.get("error.title"),
                    java.text.MessageFormat.format(lm.get("profile.avatar.error.open"), e.getMessage()));
        }
    }

    /** Abre el diálogo modal de recorte y, si el usuario confirma, sube la imagen. */
    private void openCropDialog(Image image) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/avatar-crop-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();

            AvatarCropController cropController = loader.getController();
            cropController.setImage(image);

            Stage dialog = new Stage();
            dialog.setTitle(lm.get("profile.avatar.crop.title"));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(avatarContainer.getScene().getWindow());
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();

            byte[] croppedPng = cropController.getCroppedImageBytes();
            if (croppedPng != null) {
                uploadAvatar(croppedPng);
            }
        } catch (Exception e) {
            showAlert(lm.get("error.title"),
                    java.text.MessageFormat.format(lm.get("profile.avatar.error.open"), e.getMessage()));
        }
    }

    /** Sube los bytes del avatar recortado al backend. */
    private void uploadAvatar(byte[] pngBytes) {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance().getApiService()
                        .postMultipart("/api/users/me/avatar", "file",
                                "avatar.png", "image/png", pngBytes);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        AppContext.getInstance().setHasAvatar(true);
                        avatarView.refresh();
                        loadActivityLog();
                        if (onProfileUpdated != null) onProfileUpdated.run();
                    } else {
                        showAlert(lm.get("error.title"),
                                java.text.MessageFormat.format(lm.get("profile.avatar.error.upload"), response.statusCode()));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"),
                        java.text.MessageFormat.format(lm.get("profile.avatar.error.connection"), e.getMessage())));
            }
        }).start();
    }

    /** Llama al backend para eliminar el avatar y refresca la UI. */
    private void handleRemovePhoto() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("profile.avatar.delete.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("profile.avatar.delete.content"));
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> response = AppContext.getInstance().getApiService()
                                .delete("/api/users/me/avatar");
                        Platform.runLater(() -> {
                            if (response.statusCode() == 204 || response.statusCode() == 200) {
                                AppContext.getInstance().setHasAvatar(false);
                                avatarView.refresh();
                                loadActivityLog();
                                if (onProfileUpdated != null) onProfileUpdated.run();
                            } else {
                                showAlert(lm.get("error.title"), lm.get("profile.avatar.error.delete"));
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("error.connection")));
                    }
                }).start();
            }
        });
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
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("profile.error.load")));
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
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("profile.error.stats")));
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
                createStatCard(String.valueOf(total),      lm.get("profile.stats.total"),      "#7c3aed"),
                createStatCard(String.valueOf(completed),  lm.get("common.done"),  "#22c55e"),
                createStatCard(String.valueOf(pending),    lm.get("profile.stats.pending"),    "#3b82f6"),
                createStatCard(String.valueOf(inProgress), lm.get("status.inprogress"), "#f59e0b"),
                createStatCard(String.valueOf(cancelled),  lm.get("profile.stats.cancelled"),  "#e74c3c"),
                createStatCard(String.valueOf(projects),   lm.get("common.projects"),   "#ec4899")
        );
        for (javafx.scene.Node c : statsRow.getChildren())
            HBox.setHgrow(c, Priority.ALWAYS);

        // Barra de progreso
        completionRateLabel.setText(rate + "% " + lm.get("profile.stats.rate").toLowerCase());
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
            if (date.equals(today))          group = lm.get("common.date.today");
            else if (date.equals(yesterday)) group = lm.get("activity.group.yesterday");
            else if (!date.isBefore(today.minusDays(7))) group = lm.get("activity.group.week");
            else                             group = lm.get("activity.group.older");

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
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-profile-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
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
            dialog.setTitle(lm.get("profile.edit.dialog.title"));
            dialog.setScene(new Scene(root, 400, 380));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
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
            case "TASK_CREATED"                -> lm.get("common.task.created") + name;
            case "TASK_EDITED"                 -> lm.get("common.task.edited") + name;
            case "TASK_DELETED"                -> lm.get("activity.task.deleted") + name;
            case "TASK_PERMANENTLY_DELETED"    -> lm.get("activity.task.perm.deleted") + name;
            case "TASK_RESTORED"               -> lm.get("activity.task.restored") + name;
            case "TASK_STATUS_CHANGED"         -> lm.get("common.task") + name + " → " + translateStatus(newValue);
            case "SUBTASK_CREATED"             -> lm.get("common.subtask.created") + name;
            case "SUBTASK_EDITED"              -> lm.get("common.subtask.edited") + name;
            case "SUBTASK_DELETED"             -> lm.get("common.subtask.deleted") + name;
            case "PROJECT_CREATED"             -> lm.get("common.project.created") + name;
            case "PROJECT_EDITED"              -> lm.get("common.project.edited") + name;
            case "PROJECT_DELETED"             -> lm.get("activity.project.deleted") + name;
            case "PROJECT_PERMANENTLY_DELETED" -> lm.get("activity.project.perm.deleted") + name;
            case "PROJECT_RESTORED"            -> lm.get("common.project.restored") + name;
            case "PROJECT_STATUS_CHANGED"      -> lm.get("common.project") + name + " · " +
                    translateStatus(oldValue) + " → " + translateStatus(newValue);
            case "PROFILE_UPDATED"             -> lm.get("common.profile.updated");
            case "PASSWORD_CHANGED"            -> lm.get("common.password.changed");
            default                            -> actionType;
        };
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        String key = "status.translate." + status;
        String val = lm.get(key);
        return val.startsWith("?") ? status : val;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
