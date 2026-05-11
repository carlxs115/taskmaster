package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.AvatarView;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controlador de la pantalla de perfil de usuario.
 *
 * <p>Muestra los datos personales del usuario, las estadísticas de tareas y
 * proyectos, la barra de progreso de completado y el historial de actividad
 * agrupado por fecha. También gestiona la subida, el recorte y la eliminación
 * del avatar, y abre el diálogo de edición de perfil.</p>
 *
 * @author Carlos
 */
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

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

    private final ObjectMapper    objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm           = LanguageManager.getInstance();
    private final Locale          locale       = LanguageManager.getInstance().getCurrentLocale();

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Registra el callback que se ejecutará tras actualizar el perfil o el avatar.
     *
     * @param callback acción a ejecutar al completar la actualización
     */
    public void setOnProfileUpdated(Runnable callback) {
        this.onProfileUpdated = callback;
    }

    /**
     * Inicializa la pantalla configurando el avatar y cargando el perfil,
     * las estadísticas y el historial de actividad desde el backend.
     */
    @FXML
    public void initialize() {
        setupAvatar();
        loadProfile();
        loadStats();
        loadActivityLog();
    }

    // -------------------------------------------------------------------------
    // Avatar
    // -------------------------------------------------------------------------

    /**
     * Crea el componente {@link AvatarView}, lo carga para el usuario actual
     * y añade el icono de cámara superpuesto para gestionar la foto de perfil.
     */
    private void setupAvatar() {
        avatarView = new AvatarView(80);
        avatarView.loadForCurrentUser();

        // Icono de cámara en la esquina inferior derecha del avatar
        StackPane cameraButton = new StackPane();
        cameraButton.getStyleClass().add("btn-camera-icon");
        FontIcon cameraIcon = new FontIcon("fas-camera");
        cameraIcon.getStyleClass().add("btn-camera-icon-glyph");
        cameraButton.getChildren().add(cameraIcon);
        StackPane.setAlignment(cameraButton, Pos.BOTTOM_RIGHT);
        cameraButton.setOnMouseClicked(e -> showAvatarMenu(cameraButton));

        avatarContainer.getChildren().setAll(avatarView, cameraButton);
        avatarContainer.setCursor(Cursor.DEFAULT);
    }

    /**
     * Muestra un menú contextual con las opciones de cambiar y eliminar avatar.
     * La opción de eliminar solo aparece si el usuario tiene avatar asignado.
     *
     * @param anchor nodo sobre el que se ancla el menú
     */
    private void showAvatarMenu(Node anchor) {
        ContextMenu menu = new ContextMenu();

        MenuItem change = new MenuItem(lm.get("profile.avatar.change"));
        change.setGraphic(new FontIcon("fas-camera"));
        change.setOnAction(e -> handleChangePhoto());
        menu.getItems().add(change);

        if (AppContext.getInstance().hasAvatar()) {
            MenuItem remove = new MenuItem(lm.get("profile.avatar.remove"));
            remove.setGraphic(new FontIcon("fas-trash"));
            remove.getStyleClass().add("menu-item-danger");
            remove.setOnAction(e -> handleRemovePhoto());
            menu.getItems().add(remove);
        }

        menu.show(anchor, Side.BOTTOM, 0, 4);
    }

    /**
     * Abre un {@link FileChooser} para seleccionar una imagen, valida que
     * no supere los 2 MB y, si es válida, abre el diálogo de recorte.
     */
    private void handleChangePhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(lm.get("profile.avatar.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));

        File selected = fileChooser.showOpenDialog(avatarContainer.getScene().getWindow());
        if (selected == null) return;

        // Validación de tamaño local antes de abrir el diálogo de recorte
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
                    MessageFormat.format(lm.get("profile.avatar.error.open"), e.getMessage()));
        }
    }

    /**
     * Abre el diálogo modal de recorte circular y, si el usuario confirma,
     * inicia la subida del avatar al backend.
     *
     * @param image imagen fuente seleccionada por el usuario
     */
    private void openCropDialog(Image image) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/avatar-crop-dialog.fxml"),
                    LanguageManager.getInstance().getBundle());
            VBox root = loader.load();
            AvatarCropController cropController = loader.getController();
            cropController.setImage(image);

            Stage dialog = new Stage();
            dialog.setTitle(lm.get("profile.avatar.crop.title"));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(avatarContainer.getScene().getWindow());

            Scene scene = new Scene(root);
            applyThemeToScene(scene);
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

            byte[] croppedPng = cropController.getCroppedImageBytes();
            if (croppedPng != null) uploadAvatar(croppedPng);

        } catch (Exception e) {
            showAlert(lm.get("error.title"),
                    MessageFormat.format(lm.get("profile.avatar.error.open"), e.getMessage()));
        }
    }

    /**
     * Sube los bytes PNG del avatar recortado al backend en un hilo secundario.
     *
     * @param pngBytes bytes de la imagen recortada en formato PNG
     */
    private void uploadAvatar(byte[] pngBytes) {
        Thread thread = new Thread(() -> {
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
                                MessageFormat.format(lm.get("profile.avatar.error.upload"), response.statusCode()));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"),
                        MessageFormat.format(lm.get("profile.avatar.error.connection"), e.getMessage())));
            }
        }, "profile-upload-avatar");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Muestra confirmación y, si el usuario acepta, elimina el avatar del backend.
     */
    private void handleRemovePhoto() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("profile.avatar.delete.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("profile.avatar.delete.content"));
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                Thread thread = new Thread(() -> {
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
                }, "profile-remove-avatar");
                thread.setDaemon(true);
                thread.start();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Carga de datos
    // -------------------------------------------------------------------------

    /**
     * Obtiene los datos del perfil del usuario desde el backend.
     */
    private void loadProfile() {
        Thread thread = new Thread(() -> {
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
        }, "profile-load-profile");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Obtiene las estadísticas de tareas y proyectos del usuario desde el backend.
     */
    private void loadStats() {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/users/stats");
                if (response.statusCode() == 200) {
                    JsonNode stats = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderStats(stats));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(lm.get("error.title"), lm.get("profile.error.stats")));
            }
        }, "profile-load-stats");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Obtiene el historial de actividad global del usuario desde el backend.
     */
    private void loadActivityLog() {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/activity-log");
                if (response.statusCode() == 200) {
                    JsonNode entries = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderActivityLog(entries));
                }
            } catch (Exception e) {
                log.error("Error al cargar el historial de actividad: {}", e.getMessage());
            }
        }, "profile-load-activity");
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Renderizado
    // -------------------------------------------------------------------------

    /**
     * Rellena los campos de la vista con los datos del perfil.
     *
     * @param user nodo JSON con los datos del usuario
     */
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
                        DateTimeFormatter.ofPattern("d MMMM yyyy", locale)));
            } catch (Exception ignored) {
                birthDateLabel.setText("-");
            }
        }

        // Fecha de registro
        if (user.has("createdAt") && !user.get("createdAt").isNull()) {
            try {
                LocalDateTime ca = LocalDateTime.parse(user.get("createdAt").asText());
                createdAtLabel.setText(ca.format(
                        DateTimeFormatter.ofPattern("d MMMM yyyy", locale)));
            } catch (Exception ignored) {
                createdAtLabel.setText("-");
            }
        }
    }

    /**
     * Construye las tarjetas de estadísticas y actualiza la barra de progreso.
     *
     * @param stats nodo JSON con las estadísticas del usuario
     */
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

        for (javafx.scene.Node c : statsRow.getChildren()) {
            HBox.setHgrow(c, Priority.ALWAYS);
        }

        // Barra de progreso
        completionRateLabel.setText(rate + "% " + lm.get("profile.stats.rate").toLowerCase());
        completionBarFill.prefWidthProperty().bind(
                completionBarFill.getParent() instanceof Region parent
                        ? parent.widthProperty().multiply(rate / 100.0)
                        : null
        );

        // Ancho de la barra via estilo
        completionBarFill.setStyle(
                "-fx-background-color: #22c55e; -fx-background-radius: 4px; " +
                        "-fx-pref-width: " + rate + "%;");
    }

    /**
     * Construye el historial de actividad agrupado por fecha y lo muestra en la vista.
     *
     * @param entries array JSON con las entradas del historial
     */
    private void renderActivityLog(JsonNode entries) {
        activityLogContainer.getChildren().clear();

        if (entries.isEmpty()) {
            activityLogContainer.getChildren().add(activityLogEmpty);
            return;
        }

        LocalDate today    = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Agrupamos las entradas por período de tiempo
        LinkedHashMap<String, List<JsonNode>> groups = new LinkedHashMap<>();
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
        for (Map.Entry<String, List<JsonNode>> group : groups.entrySet()) {
            // Separador entre grupos
            if (!firstGroup) {
                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: #f0f0f0;");
                activityLogContainer.getChildren().add(sep);
            }
            firstGroup = false;

            // Cabecera del grupo
            Label groupLabel = new Label(group.getKey());
            groupLabel.getStyleClass().add("sidebar-section-label");
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

                String iconLiteral = getActivityIcon(actionType);
                String color       = getActivityColor(actionType);
                String desc  = getActivityDescription(actionType, entityName, oldValue, newValue);

                LocalDateTime dt = LocalDateTime.parse(entry.get("createdAt").asText());
                String timeStr   = dt.format(DateTimeFormatter.ofPattern("HH:mm"));

                boolean isLast = i == groupEntries.size() - 1;

                FontIcon iconNode = new FontIcon(iconLiteral);
                iconNode.setIconSize(13);
                iconNode.setIconColor(Color.web(color));

                StackPane iconWrap = new StackPane(iconNode);
                iconWrap.setMinWidth(20);
                iconWrap.setAlignment(Pos.CENTER);

                Label descLabel = new Label(desc);
                descLabel.getStyleClass().add("profile-field-value");
                descLabel.setWrapText(true);
                HBox.setHgrow(descLabel, Priority.ALWAYS);

                Label timeLabel = new Label(timeStr);
                timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaaaaa;");

                HBox row = new HBox(12, iconWrap, descLabel, timeLabel);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 9 20 9 20;" +
                        (!isLast ? "-fx-border-color: transparent transparent #f5f5f5 transparent;" +
                                "-fx-border-width: 0 0 1 0;" : ""));

                activityLogContainer.getChildren().add(row);
            }
        }
    }

    /**
     * Crea una tarjeta de estadística con un número destacado y etiqueta descriptiva.
     *
     * @param number valor numérico a mostrar
     * @param label  etiqueta descriptiva
     * @param color  color hex del número
     * @return {@link VBox} con la tarjeta
     */
    private VBox createStatCard(String number, String label, String color) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("stat-card");

        Label num = new Label(number);
        num.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");
        lbl.setWrapText(true);
        lbl.setAlignment(Pos.CENTER);

        card.getChildren().addAll(num, lbl);
        return card;
    }

    // -------------------------------------------------------------------------
    // Diálogos
    // -------------------------------------------------------------------------

    /**
     * Abre el diálogo de edición de perfil y recarga los datos al cerrarlo.
     */
    @FXML
    private void handleEditProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-profile-dialog.fxml"),
                    LanguageManager.getInstance().getBundle());
            VBox root = loader.load();
            EditProfileController controller = loader.getController();
            controller.setOnProfileUpdated(() -> {
                loadProfile();
                loadActivityLog();
                if (onProfileUpdated != null) onProfileUpdated.run();
            });

            Stage dialog = new Stage();
            dialog.setTitle(lm.get("profile.edit.dialog.title"));
            Scene scene = new Scene(root, 400, 380);
            applyThemeToScene(scene);
            dialog.setScene(scene);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados de utilidad
    // -------------------------------------------------------------------------

    /**
     * Aplica el tema activo del {@link ThemeManager} a la escena indicada,
     * cargando primero el CSS base y luego el CSS del tema activo si es distinto.
     *
     * @param scene escena a la que aplicar el tema
     */
    private void applyThemeToScene(Scene scene) {
        ThemeManager tm = ThemeManager.getInstance();

        // CSS base siempre primero
        var baseResource = getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css");
        if (baseResource != null) {
            scene.getStylesheets().add(baseResource.toExternalForm());
        }

        // CSS del tema activo si es distinto al base
        String cssPath = "/com/taskmaster/taskmasterfrontend/themes/"
                + tm.getCssFileNamePublic();
        var themeResource = getClass().getResource(cssPath);
        if (themeResource != null
                && (baseResource == null
                || !themeResource.toExternalForm().equals(
                baseResource.toExternalForm()))) {
            scene.getStylesheets().add(themeResource.toExternalForm());
        }

        scene.setFill(Color.web(tm.getBgApp()));
    }

    /**
     * Devuelve el icono correspondiente al tipo de acción del historial.
     *
     * @param actionType Código del tipo de acción (p.ej. {@code "TASK_CREATED"}).
     * @return Icono representativo de la acción.
     */
    private String getActivityIcon(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED",   "SUBTASK_CREATED"   -> "fas-plus-circle";
            case "TASK_EDITED",    "SUBTASK_EDITED",
                 "PROJECT_EDITED"                       -> "fas-pen";
            case "TASK_DELETED",   "SUBTASK_DELETED",
                 "PROJECT_DELETED"                      -> "fas-trash";
            case "TASK_PERMANENTLY_DELETED",
                 "PROJECT_PERMANENTLY_DELETED"          -> "fas-times-circle";
            case "TASK_RESTORED",  "PROJECT_RESTORED"  -> "fas-undo";
            case "TASK_STATUS_CHANGED",
                 "PROJECT_STATUS_CHANGED"               -> "fas-sync-alt";
            case "PROJECT_CREATED"                      -> "fas-folder-plus";
            case "PROFILE_UPDATED"                      -> "fas-user-edit";
            case "PASSWORD_CHANGED"                     -> "fas-key";
            default                                     -> "fas-circle";
        };
    }

    /**
     * Devuelve el color hex asociado al tipo de acción del historial.
     *
     * @param actionType Código del tipo de acción.
     * @return Color en formato hex (p.ej. {@code "#22c55e"}).
     */
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

    /**
     * Construye el texto descriptivo de una entrada del historial.
     *
     * @param actionType código del tipo de acción
     * @param entityName nombre de la entidad afectada
     * @param oldValue   valor anterior, o {@code null}
     * @param newValue   valor posterior, o {@code null}
     * @return texto descriptivo localizado
     */
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

    /**
     * Traduce un código de estado del backend a su etiqueta localizada.
     *
     * @param status Código de estado (p.ej. {@code "IN_PROGRESS"}).
     * @return Etiqueta localizada, o el propio código si no hay traducción.
     */
    private String translateStatus(String status) {
        if (status == null) return "";
        String key = "status.translate." + status;
        String val = lm.get(key);
        return val.startsWith("?") ? status : val;
    }

    /**
     * Muestra un diálogo de información con el título y mensaje indicados.
     *
     * @param title   Título del diálogo.
     * @param message Mensaje a mostrar.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}