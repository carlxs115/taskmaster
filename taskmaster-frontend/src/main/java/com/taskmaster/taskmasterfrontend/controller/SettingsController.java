package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controlador de la pantalla de ajustes.
 *
 * <p>Permite configurar el tema visual, el idioma, el formato de fecha,
 * el formato de hora y los días de retención de la papelera. Los ajustes
 * de tema y retención se persisten en el backend; el idioma y los formatos
 * de fecha y hora se gestionan localmente mediante sus respectivos managers.</p>
 *
 * @author Carlos
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    // ── Papelera ──
    @FXML private RadioButton days7;
    @FXML private RadioButton days15;
    @FXML private RadioButton days30;
    @FXML private Label retentionStatusLabel;

    // ── Idioma ──
    @FXML private RadioButton langEs;
    @FXML private RadioButton langEn;
    @FXML private Label languageStatusLabel;

    // ── Formato hora ──
    @FXML private RadioButton timeSystem;
    @FXML private RadioButton time24h;
    @FXML private RadioButton time12h;
    @FXML private Label timeFormatStatusLabel;

    // ── Formato fecha ──
    @FXML private ComboBox<String> dateFormatCombo;
    @FXML private Label dateFormatStatusLabel;

    // ── Apariencia ──
    @FXML private FlowPane themesContainer;
    @FXML private Label themeStatusLabel;

    // ── Copia de seguridad ──
    @FXML private Button createBackupButton;
    @FXML private Button restoreBackupButton;
    @FXML private Label backupStatusLabel;
    @FXML private Label restoreStatusLabel;
    @FXML private Label backupUnavailableLabel;
    @FXML private VBox restorePendingBox;
    @FXML private VBox restoreFailedBox;

    // ── Exportar e importar datos ──
    @FXML private Button exportJsonButton;
    @FXML private Button exportCsvButton;
    @FXML private Button importButton;
    @FXML private Label exportStatusLabel;
    @FXML private Label importStatusLabel;

    /** Tamaño máximo de una copia aceptado por el backend (256 MB). */
    private static final long MAX_BACKUP_BYTES = 256L * 1024 * 1024;

    /** Tamaño máximo de un archivo de importación aceptado por el backend (32 MB). */
    private static final long MAX_IMPORT_BYTES = 32L * 1024 * 1024;

    /** Se ejecuta tras una importación correcta para refrescar proyectos y tareas. */
    private Runnable onDataImported;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Datos de cada tema disponible: enum, nombre, color acento, color fondo,
     * color acento secundario.
     */
    private static final Object[][] THEME_DATA = {
            { ThemeManager.Theme.AMATISTA,        "Amatista",      "#7c3aed", "#ffffff", "#a78bfa" },
            { ThemeManager.Theme.PRADERA,         "Pradera",       "#10b981", "#ffffff", "#34d399" },
            { ThemeManager.Theme.AMBAR,           "Ámbar",         "#f59e0b", "#ffffff", "#fbbf24" },
            { ThemeManager.Theme.SAKURA,          "Sakura",        "#ec4899", "#ffffff", "#f472b6" },
            { ThemeManager.Theme.ARTICO,          "Ártico",        "#0ea5e9", "#f0f9ff", "#38bdf8" },
            { ThemeManager.Theme.PERLA,           "Perla",         "#3b82f6", "#f0f4ff", "#60a5fa" },
            { ThemeManager.Theme.OCEANO,          "Océano",        "#3b82f6", "#0f2447", "#60a5fa" },
            { ThemeManager.Theme.AURORA_BOREALIS, "Aurora Boreal", "#64FFDA", "#0B1426", "#82B1FF" },
            { ThemeManager.Theme.AMATISTA_DARK,   "Amatista Dark", "#a78bfa", "#13131f", "#7c3aed" },
            { ThemeManager.Theme.NOCHE,           "Noche",         "#22d3ee", "#000000", "#67e8f9" },
            { ThemeManager.Theme.VIGILANTE,       "Vigilante",     "#facc15", "#080808", "#fde047" },
            { ThemeManager.Theme.HACKER,          "Matrix",        "#00ff41", "#000000", "#39ff6e" },
            { ThemeManager.Theme.DRAGONSLAYER,    "DragonSlayer",  "#ff0000", "#000000", "#ff3333" },
            { ThemeManager.Theme.LUZ,             "Luz",           "#ec4899", "#080808", "#f472b6" },
    };

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Inicializa la pantalla cargando los ajustes del backend, preseleccionando
     * el idioma y el formato de hora actuales, configurando el combo de formato
     * de fecha y construyendo el selector visual de temas.
     */
    @FXML
    public void initialize() {
        loadSettings();
        loadBackupStatus();

        // Preseleccionamos el idioma activo
        if (lm.isSpanish()) langEs.setSelected(true);
        else langEn.setSelected(true);

        // Preseleccionamos el formato de hora activo
        switch (TimeFormatManager.getInstance().getCurrentFormat()) {
            case H24 -> time24h.setSelected(true);
            case H12 -> time12h.setSelected(true);
            default -> timeSystem.setSelected(true);
        }

        // Ocultamos las etiquetas de estado al cambiar de idioma
        lm.bundleProperty().addListener((obs, oldBundle, newBundle) -> {
            languageStatusLabel.setVisible(false);
            retentionStatusLabel.setVisible(false);
            timeFormatStatusLabel.setVisible(false);
            backupStatusLabel.setVisible(false);
            restoreStatusLabel.setVisible(false);
            exportStatusLabel.setVisible(false);
            importStatusLabel.setVisible(false);
        });

        // Combo de formato de fecha
        for (DateFormatManager.DateFormat fmt : DateFormatManager.DateFormat.values()) {
            dateFormatCombo.getItems().add(DateFormatManager.getLabel(fmt));
        }
        dateFormatCombo.getSelectionModel().select(
                DateFormatManager.getInstance().getCurrentFormat().ordinal());

        buildThemeSelector();
    }

    // -------------------------------------------------------------------------
    // Selector de temas
    // -------------------------------------------------------------------------

    /**
     * Construye las tarjetas de selección de tema en el {@link FlowPane}.
     * Cada tarjeta muestra el nombre y dos círculos de color (acento y fondo),
     * o corazones en el caso del tema Luz. La tarjeta activa aparece resaltada.
     */
    private void buildThemeSelector() {
        ThemeManager.Theme current = ThemeManager.getInstance().getCurrentTheme();
        themesContainer.getChildren().clear();

        for (Object[] data : THEME_DATA) {
            ThemeManager.Theme theme      = (ThemeManager.Theme) data[0];
            String name                   = (String) data[1];
            String accent                 = (String) data[2];
            String bg                     = (String) data[3];
            String accentLight            = data.length > 4 ? (String) data[4] : accent;
            boolean isSelected            = theme == current;
            boolean isLuz                 = theme == ThemeManager.Theme.LUZ;

            VBox card = new VBox(6);
            card.setAlignment(Pos.CENTER);
            card.setPrefWidth(100);
            card.setPadding(new Insets(10, 8, 10, 8));
            card.setStyle(buildCardStyle(bg, accent, isSelected));
            card.setUserData(theme);
            card.setCursor(Cursor.HAND);

            // Preview con círculos de color o corazones para el tema Luz
            HBox dots = new HBox(4);
            dots.setAlignment(Pos.CENTER);
            String[] dotColors = { accent, accentLight };
            for (String dotColor : dotColors) {
                if (isLuz) {
                    Label heart = new Label("♥");
                    heart.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 14px;");
                    dots.getChildren().add(heart);
                } else {
                    Circle dot = new Circle(7);
                    dot.setFill(Color.web(dotColor));
                    dots.getChildren().add(dot);
                }
            }

            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; " +
                    "-fx-text-fill: " + (isDarkBg(bg) ? "#ffffff" : "#2d2d2d") + "; " +
                    "-fx-wrap-text: true; -fx-text-alignment: center;");
            nameLabel.setMaxWidth(80);
            nameLabel.setAlignment(Pos.CENTER);

            card.getChildren().addAll(dots, nameLabel);
            card.setOnMouseClicked(e -> handleThemeSelected(theme, card));
            themesContainer.getChildren().add(card);
        }
    }

    /**
     * Genera el estilo CSS inline de una tarjeta de tema.
     *
     * @param bg       color de fondo en hex
     * @param accent   color de acento en hex
     * @param selected {@code true} si es el tema activo
     * @return cadena de estilo CSS
     */
    private String buildCardStyle(String bg, String accent, boolean selected) {
        String border = selected ? accent : "#e0e0e0";
        String borderWidth = selected ? "2.5" : "1";
        String shadow = selected ? "-fx-effect: dropshadow(gaussian, " + accent + ", 8, 0.4, 0, 0);" : "";
        return "-fx-background-color: " + bg + "; " +
                "-fx-background-radius: 10px; " +
                "-fx-border-color: " + border + "; " +
                "-fx-border-radius: 10px; " +
                "-fx-border-width: " + borderWidth + "; " +
                "-fx-cursor: hand; " +
                shadow;
    }

    /**
     * Determina si un color de fondo es oscuro para elegir el color del texto.
     *
     * @param hex color en formato hex
     * @return {@code true} si la luminancia es inferior a 0.5
     */
    private boolean isDarkBg(String hex) {
        try {
            Color c = Color.web(hex);
            double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
            return luminance < 0.5;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Aplica el tema seleccionado, actualiza los estilos de todas las tarjetas
     * y guarda el tema en el backend en un hilo secundario.
     *
     * @param theme        tema seleccionado
     * @param selectedCard tarjeta pulsada
     */
    private void handleThemeSelected(ThemeManager.Theme theme, VBox selectedCard) {
        ThemeManager.getInstance().applyTheme(theme);

        // Actualizamos el estilo de todas las tarjetas
        for (var node : themesContainer.getChildren()) {
            if (node instanceof VBox card) {
                ThemeManager.Theme cardTheme = (ThemeManager.Theme) card.getUserData();
                for (Object[] data : THEME_DATA) {
                    if (data[0] == cardTheme) {
                        card.setStyle(buildCardStyle(
                                (String) data[3],
                                (String) data[2],
                                card == selectedCard));
                        break;
                    }
                }
            }
        }
        saveThemeAsync(theme);
    }

    // -------------------------------------------------------------------------
    // Carga de ajustes
    // -------------------------------------------------------------------------

    /**
     * Obtiene los ajustes del usuario desde el backend y preselecciona
     * los días de retención y el tema guardado.
     */
    private void loadSettings() {
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/settings");
                if (response.statusCode() == 200) {
                    JsonNode settings = objectMapper.readTree(response.body());
                    int days = settings.get("trashRetentionDays").asInt();

                    Platform.runLater(() -> {
                        if (days == 7) days7.setSelected(true);
                        else if (days == 15) days15.setSelected(true);
                        else days30.setSelected(true);
                        buildThemeSelector();
                    });
                }
            } catch (Exception e) {
                log.error("Error al cargar los ajustes: {}", e.getMessage());
            }
        }, "settings-load");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Acciones de guardado
    // -------------------------------------------------------------------------

    /**
     * Guarda el tema seleccionado en el backend en un hilo secundario.
     *
     * @param theme tema a guardar
     */
    private void saveThemeAsync(ThemeManager.Theme theme) {
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .patch("/api/settings/theme?theme=" + theme.name(), null);
                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        themeStatusLabel.setText(lm.get("settings.saved"));
                        themeStatusLabel.setStyle(
                                "-fx-font-size: 12px; -fx-text-fill: #2ecc71;");
                        themeStatusLabel.setVisible(true);
                    }
                });
            } catch (Exception e) {
                log.error("Error al guardar el tema: {}", e.getMessage());
                Platform.runLater(() -> {
                    themeStatusLabel.setText(lm.get("settings.connection.error"));
                    themeStatusLabel.setStyle(
                            "-fx-font-size: 12px; -fx-text-fill: #e74c3c;");
                    themeStatusLabel.setVisible(true);
                });
            }
        }, "settings-save-theme");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Aplica el formato de fecha seleccionado y muestra confirmación.
     */
    @FXML
    private void handleSaveDateFormat() {
        int idx = dateFormatCombo.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;
        DateFormatManager.getInstance().setFormat(DateFormatManager.DateFormat.values()[idx]);

        dateFormatStatusLabel.setText(lm.get("settings.saved"));
        dateFormatStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2ecc71;");
        dateFormatStatusLabel.setVisible(true);
    }

    /**
     * Aplica el formato de hora seleccionado y muestra confirmación.
     */
    @FXML
    private void handleSaveTimeFormat() {
        TimeFormatManager tfm = TimeFormatManager.getInstance();
        if (time24h.isSelected()) tfm.setFormat(TimeFormatManager.TimeFormat.H24);
        else if (time12h.isSelected()) tfm.setFormat(TimeFormatManager.TimeFormat.H12);
        else tfm.setFormat(TimeFormatManager.TimeFormat.SYSTEM);

        timeFormatStatusLabel.setText(lm.get("settings.saved"));
        timeFormatStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2ecc71;");
        timeFormatStatusLabel.setVisible(true);
    }

    /**
     * Envía al backend los días de retención de papelera seleccionados
     * y muestra el resultado de la operación.
     */
    @FXML
    private void handleSaveRetention () {
        int days = days7.isSelected() ? 7 : days15.isSelected() ? 15 : 30;

        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .patch("/api/settings/trash-retention?days=" + days, null);
                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        retentionStatusLabel.setText(lm.get("settings.saved"));
                        retentionStatusLabel.setVisible(true);
                    } else {
                        retentionStatusLabel.setText(lm.get("settings.error"));
                        retentionStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                        retentionStatusLabel.setVisible(true);
                    }
                });
            } catch (Exception e) {
                log.error("Error al guardar la retención: {}", e.getMessage());
                Platform.runLater(() -> {
                    retentionStatusLabel.setText(lm.get("settings.connection.error"));
                    retentionStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    retentionStatusLabel.setVisible(true);
                });
            }
        }, "settings-save-retention");
        thread.setDaemon(true);
        thread.start();
    }

    // -------------------------------------------------------------------------
    // Copia de seguridad
    // -------------------------------------------------------------------------

    /**
     * Consulta al backend si la copia de seguridad está disponible, si hay una
     * restauración pendiente y si la última falló, y actualiza botones y avisos.
     *
     * <p>Se llama desde el hilo de JavaFX: oculta antes los mensajes de estado de las
     * operaciones anteriores (p. ej. «Copia guardada») para que no queden a la vista.</p>
     */
    private void loadBackupStatus() {
        backupStatusLabel.setVisible(false);
        restoreStatusLabel.setVisible(false);
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/backup/status");
                if (response.statusCode() != 200) return;
                JsonNode json = objectMapper.readTree(response.body());
                boolean available = json.path("available").asBoolean(false);
                boolean pending = json.path("restorePending").asBoolean(false);
                boolean failed = json.path("lastRestoreFailed").asBoolean(false);
                Platform.runLater(() -> {
                    createBackupButton.setDisable(!available);
                    restoreBackupButton.setDisable(!available);
                    setShown(backupUnavailableLabel, !available);
                    setShown(restorePendingBox, pending);
                    setShown(restoreFailedBox, failed);
                });
            } catch (Exception e) {
                log.error("Error al cargar el estado de la copia de seguridad: {}", e.getMessage());
            }
        }, "settings-backup-status");
        thread.setDaemon(true);
        thread.start();
    }

    /** Muestra u oculta un nodo sin que reserve espacio cuando está oculto. */
    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /**
     * Muestra un mensaje de estado en la etiqueta indicada.
     *
     * @param label   etiqueta de estado
     * @param text    texto ya traducido
     * @param success {@code true} para el color de éxito, {@code false} para el de error
     */
    private void showBackupStatus(Label label, String text, boolean success) {
        label.setText(text);
        label.setStyle("-fx-text-fill: " + (success ? "#2ecc71" : "#e74c3c") + ";");
        label.setVisible(true);
    }

    /** Ventana propietaria de los diálogos de esta vista. */
    private Window ownerWindow() {
        return backupStatusLabel.getScene() != null ? backupStatusLabel.getScene().getWindow() : null;
    }

    /**
     * Pide al usuario dónde guardar la copia y la descarga del backend.
     */
    @FXML
    private void handleCreateBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(lm.get("settings.backup.chooser.save"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP (*.zip)", "*.zip"));
        chooser.setInitialFileName("taskmaster-copia-" + LocalDate.now() + ".zip");
        File file = chooser.showSaveDialog(ownerWindow());
        if (file == null) return;

        createBackupButton.setDisable(true);
        backupStatusLabel.setVisible(false);
        Thread thread = new Thread(() -> {
            boolean ok;
            try {
                AppContext.getInstance().getApiService().downloadToFile("/api/backup", file.toPath());
                ok = true;
            } catch (Exception e) {
                log.error("Error al crear la copia de seguridad: {}", e.getMessage());
                ok = false;
            }
            boolean success = ok;
            Platform.runLater(() -> {
                createBackupButton.setDisable(false);
                showBackupStatus(backupStatusLabel,
                        lm.get(success ? "settings.backup.create.done" : "settings.backup.create.error"),
                        success);
            });
        }, "settings-create-backup");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Elige una copia, pide la contraseña y una segunda confirmación y la envía
     * al backend, que la valida y la deja pendiente hasta el próximo arranque.
     */
    @FXML
    private void handleRestoreBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(lm.get("settings.backup.chooser.open"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP (*.zip)", "*.zip"));
        File file = chooser.showOpenDialog(ownerWindow());
        if (file == null) return;

        restoreStatusLabel.setVisible(false);
        if (file.length() > MAX_BACKUP_BYTES) {
            showBackupStatus(restoreStatusLabel, lm.get("settings.backup.restore.error.size"), false);
            return;
        }

        PasswordField pf = new PasswordField();
        pf.setPromptText(lm.get("common.password.prompt"));

        Label warningLabel = new Label(lm.get("settings.backup.restore.warning"));
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;");

        VBox content = new VBox(8, warningLabel, pf);

        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle(lm.get("settings.backup.restore.title"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(pf::requestFocus);

        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK))
                .setText(lm.get("common.confirm"));
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL))
                .setText(lm.get("common.cancel"));

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;

            String password = pf.getText();
            if (password.isBlank()) {
                showBackupStatus(restoreStatusLabel, lm.get("settings.backup.password.empty"), false);
                return;
            }

            // Segunda confirmación antes de una operación que sustituye todos los datos
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(lm.get("settings.backup.restore.title"));
            confirm.setHeaderText(lm.get("settings.backup.restore.confirm.header"));
            confirm.setContentText(lm.get("settings.backup.restore.confirm.content"));

            ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK))
                    .setText(lm.get("common.confirm"));
            ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL))
                    .setText(lm.get("common.cancel"));

            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) restoreBackupAsync(password, file);
            });
        });
    }

    /**
     * Envía la copia al backend en un hilo secundario y gestiona la respuesta.
     *
     * @param password contraseña confirmada por el usuario
     * @param file     copia elegida
     */
    private void restoreBackupAsync(String password, File file) {
        restoreBackupButton.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8);
                HttpResponse<String> response;
                try {
                    response = AppContext.getInstance()
                            .getApiService()
                            .postFile("/api/backup/restore?password=" + encodedPassword,
                                    file.toPath(), "application/zip");
                } catch (IOException e) {
                    // Si el backend sigue respondiendo, cortó el envío al rechazar la copia
                    // (p. ej. tras un 413 a mitad de lectura): es un error de la copia, no de conexión
                    log.error("Error al enviar la copia de seguridad: {}", e.getMessage());
                    String key = isBackendReachable()
                            ? "settings.backup.restore.error"
                            : "settings.connection.error";
                    Platform.runLater(() -> {
                        restoreBackupButton.setDisable(false);
                        showBackupStatus(restoreStatusLabel, lm.get(key), false);
                    });
                    return;
                }
                int status = response.statusCode();

                if (status == 202) {
                    List<String> usernames = new ArrayList<>();
                    for (JsonNode name : objectMapper.readTree(response.body()).path("usernames")) {
                        usernames.add(name.asText());
                    }
                    Platform.runLater(() -> {
                        restoreBackupButton.setDisable(false);
                        showRestoreReadyDialog(usernames);
                    });
                } else {
                    String key = status == 413
                            ? "settings.backup.restore.error.size"
                            : "settings.backup.restore.error";
                    Platform.runLater(() -> {
                        restoreBackupButton.setDisable(false);
                        showBackupStatus(restoreStatusLabel, lm.get(key), false);
                    });
                }
            } catch (Exception e) {
                log.error("Error al preparar la restauración: {}", e.getMessage());
                Platform.runLater(() -> {
                    restoreBackupButton.setDisable(false);
                    showBackupStatus(restoreStatusLabel, lm.get("settings.connection.error"), false);
                });
            }
        }, "settings-restore-backup");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Comprueba si el backend responde, con {@code /api/auth/ping} (no requiere sesión).
     *
     * @return {@code true} si el backend contesta con cualquier código HTTP
     */
    private boolean isBackendReachable() {
        try {
            AppContext.getInstance().getApiService().get("/api/auth/ping");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Informa de que la restauración queda preparada y ofrece cerrar TaskMaster
     * para aplicarla en el siguiente arranque.
     *
     * @param usernames cuentas que contiene la copia
     */
    private void showRestoreReadyDialog(List<String> usernames) {
        ButtonType closeButton = new ButtonType(lm.get("settings.backup.restore.close"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType laterButton = new ButtonType(lm.get("settings.backup.restore.later"),
                ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert dialog = new Alert(Alert.AlertType.INFORMATION,
                MessageFormat.format(lm.get("settings.backup.restore.ready.content"),
                        String.join(", ", usernames)),
                closeButton, laterButton);
        dialog.setTitle(lm.get("settings.backup.restore.ready.title"));
        dialog.setHeaderText(lm.get("settings.backup.restore.ready.title"));

        boolean close = dialog.showAndWait().filter(b -> b == closeButton).isPresent();
        if (close) {
            // Platform.exit() acaba llamando a MainApp.stop(), que también para el backend
            Platform.exit();
        } else {
            loadBackupStatus();
        }
    }

    /**
     * Cancela la restauración pendiente en el backend (y descarta el aviso de una
     * restauración fallida) y recarga el estado.
     */
    @FXML
    private void handleCancelRestore() {
        Thread thread = new Thread(() -> {
            boolean ok;
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .delete("/api/backup/restore");
                ok = response.statusCode() == 204 || response.statusCode() == 200;
            } catch (Exception e) {
                log.error("Error al cancelar la restauración: {}", e.getMessage());
                ok = false;
            }
            boolean success = ok;
            Platform.runLater(() -> {
                // Recargar oculta los mensajes de estado; el error, si lo hay, se muestra después
                loadBackupStatus();
                if (!success) {
                    showBackupStatus(restoreStatusLabel, lm.get("settings.connection.error"), false);
                }
            });
        }, "settings-cancel-restore");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Registra la acción que refresca la barra lateral y las tareas tras importar datos.
     *
     * @param onDataImported acción a ejecutar en el hilo de JavaFX
     */
    public void setOnDataImported(Runnable onDataImported) {
        this.onDataImported = onDataImported;
    }

    /** Pide dónde guardar la exportación en JSON y la descarga del backend. */
    @FXML
    private void handleExportJson() {
        exportData("/api/data/export/json", "settings.data.chooser.json",
                "JSON (*.json)", "*.json", "taskmaster-datos-" + LocalDate.now() + ".json");
    }

    /** Pide dónde guardar la exportación en CSV (en el idioma de la interfaz) y la descarga. */
    @FXML
    private void handleExportCsv() {
        exportData("/api/data/export/csv?lang=" + (lm.isSpanish() ? "es" : "en"),
                "settings.data.chooser.csv", "CSV (*.csv)", "*.csv",
                "taskmaster-tareas-" + LocalDate.now() + ".csv");
    }

    /**
     * Elige el destino y descarga una exportación en un hilo secundario.
     *
     * @param path        ruta de la API que genera el archivo
     * @param titleKey    clave del título del selector de archivos
     * @param filterName  descripción del filtro de extensión
     * @param filterGlob  patrón del filtro de extensión
     * @param initialName nombre de archivo propuesto
     */
    private void exportData(String path, String titleKey, String filterName, String filterGlob,
                            String initialName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(lm.get(titleKey));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, filterGlob));
        chooser.setInitialFileName(initialName);
        File file = chooser.showSaveDialog(ownerWindow());
        if (file == null) return;

        exportJsonButton.setDisable(true);
        exportCsvButton.setDisable(true);
        exportStatusLabel.setVisible(false);
        Thread thread = new Thread(() -> {
            boolean ok;
            try {
                AppContext.getInstance().getApiService().downloadToFile(path, file.toPath());
                ok = true;
            } catch (Exception e) {
                log.error("Error al exportar los datos: {}", e.getMessage());
                ok = false;
            }
            boolean success = ok;
            Platform.runLater(() -> {
                exportJsonButton.setDisable(false);
                exportCsvButton.setDisable(false);
                showBackupStatus(exportStatusLabel,
                        lm.get(success ? "settings.data.export.done" : "settings.data.export.error"),
                        success);
            });
        }, "settings-export-data");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Elige un archivo JSON exportado, pide confirmación y lo envía al backend,
     * que lo valida entero y lo añade como datos nuevos de la cuenta.
     */
    @FXML
    private void handleImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(lm.get("settings.data.chooser.import"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"));
        File file = chooser.showOpenDialog(ownerWindow());
        if (file == null) return;

        importStatusLabel.setVisible(false);
        if (file.length() > MAX_IMPORT_BYTES) {
            showBackupStatus(importStatusLabel, lm.get("settings.data.import.error.size"), false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("settings.data.import.title"));
        confirm.setHeaderText(lm.get("settings.data.import.confirm.header"));
        confirm.setContentText(lm.get("settings.data.import.confirm.content"));

        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK))
                .setText(lm.get("common.confirm"));
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL))
                .setText(lm.get("common.cancel"));

        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) importDataAsync(file);
        });
    }

    /**
     * Envía el archivo al backend en un hilo secundario y gestiona la respuesta.
     *
     * @param file archivo JSON elegido
     */
    private void importDataAsync(File file) {
        importButton.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                HttpResponse<String> response;
                try {
                    response = AppContext.getInstance()
                            .getApiService()
                            .postFile("/api/data/import", file.toPath(), "application/json");
                } catch (IOException e) {
                    // Si el backend sigue respondiendo, cortó el envío al rechazar el archivo
                    // (p. ej. tras un 413 a mitad de lectura): es un error del archivo, no de conexión
                    log.error("Error al enviar el archivo de importación: {}", e.getMessage());
                    String key = isBackendReachable()
                            ? "settings.data.import.error.generic"
                            : "settings.connection.error";
                    Platform.runLater(() -> {
                        importButton.setDisable(false);
                        showBackupStatus(importStatusLabel, lm.get(key), false);
                    });
                    return;
                }
                int status = response.statusCode();

                if (status == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    String message = MessageFormat.format(lm.get("settings.data.import.done"),
                            json.path("projects").asInt(), json.path("tasks").asInt(),
                            json.path("workLogs").asInt(), json.path("dependencies").asInt());
                    Platform.runLater(() -> {
                        importButton.setDisable(false);
                        showBackupStatus(importStatusLabel, message, true);
                        if (onDataImported != null) onDataImported.run();
                    });
                } else if (status == 400) {
                    String detail = objectMapper.readTree(response.body()).path("error").asText("");
                    String message = detail.isBlank()
                            ? lm.get("settings.data.import.error.generic")
                            : MessageFormat.format(lm.get("settings.data.import.error"), detail);
                    Platform.runLater(() -> {
                        importButton.setDisable(false);
                        showBackupStatus(importStatusLabel, message, false);
                    });
                } else {
                    String key = status == 413
                            ? "settings.data.import.error.size"
                            : "settings.data.import.error.generic";
                    Platform.runLater(() -> {
                        importButton.setDisable(false);
                        showBackupStatus(importStatusLabel, lm.get(key), false);
                    });
                }
            } catch (Exception e) {
                log.error("Error al importar los datos: {}", e.getMessage());
                Platform.runLater(() -> {
                    importButton.setDisable(false);
                    showBackupStatus(importStatusLabel, lm.get("settings.connection.error"), false);
                });
            }
        }, "settings-import-data");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Aplica el idioma seleccionado, lo persiste localmente y recarga
     * la vista de ajustes tras una breve pausa para que el mensaje sea visible.
     */
    @FXML
    private void handleSaveLanguage() {
        // Locale.of() es la forma correcta en Java 19+
        Locale locale = langEs.isSelected() ? Locale.of("es") : Locale.ENGLISH;
        lm.setLocale(locale);
        lm.saveLocalePreference(locale);

        languageStatusLabel.setText(lm.get("settings.saved"));
        languageStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #f59e0b;");
        languageStatusLabel.setVisible(true);

        // Esperar 500ms para que el mensaje sea visible antes de recargar la vista
        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(e -> reloadThisView());
        pause.play();
    }

    /**
     * Recarga la vista de ajustes en el idioma actualizado reemplazando
     * el nodo actual en el layout del controlador principal.
     */
    private void reloadThisView() {
        try {
            Node currentView = retentionStatusLabel.getScene().lookup("#settingsRoot");
            HBox parent = (HBox) currentView.getParent();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/settings-view.fxml"),
                    lm.getBundle());
            VBox newView = loader.load();
            // La vista recargada conserva el refresco tras importar datos
            if (loader.getController() instanceof SettingsController sc) {
                sc.setOnDataImported(onDataImported);
            }
            HBox.setHgrow(newView, Priority.ALWAYS);
            newView.setUserData("settings");

            int idx = parent.getChildren().indexOf(currentView);
            parent.getChildren().set(idx, newView);

        } catch (Exception e) {
            log.error("Error al recargar la vista de ajustes: {}", e.getMessage());
        }
    }
}