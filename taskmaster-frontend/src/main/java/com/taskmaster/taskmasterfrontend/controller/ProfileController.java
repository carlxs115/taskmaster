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

    /**
     * Registra el callback que se ejecutará tras actualizar el perfil o el avatar,
     * para que el controlador padre pueda refrescar la UI si es necesario.
     *
     * @param callback Acción a ejecutar al completar la actualización.
     */
    public void setOnProfileUpdated(Runnable callback) {
        this.onProfileUpdated = callback;
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();
    Locale locale = LanguageManager.getInstance().getCurrentLocale();

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

    /**
     * Crea el componente {@link AvatarView}, lo carga para el usuario actual
     * y añade el icono de cámara superpuesto para gestionar la foto de perfil.
     */
    private void setupAvatar() {
        avatarView = new AvatarView(80);
        avatarView.loadForCurrentUser();

        // Icono de cámara sobre el avatar (esquina inferior derecha)
        Label cameraIcon = new Label("📷");
        cameraIcon.getStyleClass().add("btn-camera-icon");
        StackPane.setAlignment(cameraIcon, Pos.BOTTOM_RIGHT);
        cameraIcon.setOnMouseClicked(e -> showAvatarMenu(cameraIcon));

        avatarContainer.getChildren().setAll(avatarView, cameraIcon);
        avatarContainer.setCursor(Cursor.DEFAULT);
    }

    /**
     * Muestra un menú contextual con las opciones "Cambiar foto" y,
     * si el usuario tiene avatar, "Eliminar foto".
     *
     * @param anchor Nodo sobre el que se ancla el menú contextual.
     */
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

    /**
     * Abre el diálogo modal de recorte circular con la imagen seleccionada
     * y, si el usuario confirma, inicia la subida del avatar al backend.
     *
     * @param image Imagen fuente seleccionada por el usuario.
     */
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

            Scene scene = new Scene(root);
            applyThemeToScene(scene);
            dialog.setScene(scene);

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

    /**
     * Sube los bytes PNG del avatar recortado al backend en un hilo secundario
     * y refresca el avatar y el historial si la operación es exitosa.
     *
     * @param pngBytes Bytes de la imagen recortada en formato PNG.
     */
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

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * el avatar del backend y refresca la UI.
     */
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

    /**
     * Obtiene los datos del perfil del usuario desde el backend
     * y los renderiza en la vista.
     */
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

    /**
     * Obtiene las estadísticas de tareas y proyectos del usuario desde
     * el backend y las renderiza en la vista.
     */
    private void loadStats() {
        new Thread(() -> {
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
        }).start();
    }

    /**
     * Obtiene el historial de actividad global del usuario desde el backend
     * y lo renderiza agrupado por fecha.
     */
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

    /**
     * Rellena los campos de la vista con los datos del perfil recibidos del backend.
     *
     * @param user Nodo JSON con los datos del usuario.
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
     * Construye las tarjetas de estadísticas y actualiza la barra de progreso
     * de completado con los datos recibidos del backend.
     *
     * @param stats Nodo JSON con las estadísticas del usuario.
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

    /**
     * Construye el historial de actividad agrupado por fecha (Hoy, Ayer,
     * Esta semana, Anteriores) y lo muestra en el contenedor de la vista.
     *
     * @param entries Array JSON con las entradas del historial de actividad.
     */
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
                descLabel.getStyleClass().add("profile-field-value");
                descLabel.setWrapText(true);
                HBox.setHgrow(descLabel, javafx.scene.layout.Priority.ALWAYS);

                Label timeLabel = new Label(timeStr);
                timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaaaaa;");

                row.getChildren().addAll(iconLabel, descLabel, timeLabel);
                activityLogContainer.getChildren().add(row);
            }
        }
    }

    /**
     * Crea una tarjeta de estadística con un número destacado y una etiqueta descriptiva.
     *
     * @param number Valor numérico a mostrar.
     * @param label  Etiqueta descriptiva del valor.
     * @param color  Color hex del número destacado.
     * @return {@link VBox} con el contenido de la tarjeta.
     */
    private VBox createStatCard(String number, String label, String color) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER);
        Label num = new Label(number);
        num.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");
        lbl.setWrapText(true);
        lbl.setAlignment(Pos.CENTER);
        card.getChildren().addAll(num, lbl);
        return card;
    }

    /**
     * Abre el diálogo modal de edición de perfil y, al cerrarlo,
     * recarga el perfil y el historial de actividad.
     */
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

            Scene scene = new Scene(root, 400, 380);
            applyThemeToScene(scene);
            dialog.setScene(scene);

            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (Exception e) {
            showAlert(lm.get("error.title"), lm.get("error.open.dialog"));
        }
    }

    /**
     * Aplica el tema activo del {@link com.taskmaster.taskmasterfrontend.util.ThemeManager}
     * a la escena indicada, cargando primero el CSS base y luego el tema seleccionado.
     *
     * @param scene Escena a la que aplicar el tema.
     */
    private void applyThemeToScene(Scene scene) {
        com.taskmaster.taskmasterfrontend.util.ThemeManager tm =
                com.taskmaster.taskmasterfrontend.util.ThemeManager.getInstance();
        // Cargar siempre el CSS base primero
        String baseUrl = getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css") != null
                ? getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css").toExternalForm()
                : null;
        if (baseUrl != null) scene.getStylesheets().add(baseUrl);
        // Luego el tema activo si no es Amatista
        String cssFile = "/com/taskmaster/taskmasterfrontend/themes/"
                + tm.getCssFileNamePublic();
        String themeUrl = getClass().getResource(cssFile) != null
                ? getClass().getResource(cssFile).toExternalForm()
                : null;
        if (themeUrl != null && !themeUrl.equals(baseUrl))
            scene.getStylesheets().add(themeUrl);
        // Fondo del Scene
        scene.setFill(javafx.scene.paint.Color.web(tm.getBgApp()));
    }

    /**
     * Devuelve el icono emoji correspondiente al tipo de acción del historial.
     *
     * @param actionType Código del tipo de acción (p.ej. {@code "TASK_CREATED"}).
     * @return Emoji representativo de la acción.
     */
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
     * Construye el texto descriptivo de una entrada del historial de actividad
     * a partir del tipo de acción, el nombre de la entidad y los valores de cambio.
     *
     * @param actionType Código del tipo de acción.
     * @param entityName Nombre de la entidad afectada.
     * @param oldValue   Valor anterior al cambio, o {@code null} si no aplica.
     * @param newValue   Valor posterior al cambio, o {@code null} si no aplica.
     * @return Texto descriptivo localizado de la acción.
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
