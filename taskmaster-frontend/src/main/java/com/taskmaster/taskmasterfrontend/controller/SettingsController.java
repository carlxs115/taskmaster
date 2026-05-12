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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
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
            HBox.setHgrow(newView, Priority.ALWAYS);
            newView.setUserData("settings");

            int idx = parent.getChildren().indexOf(currentView);
            parent.getChildren().set(idx, newView);

        } catch (Exception e) {
            log.error("Error al recargar la vista de ajustes: {}", e.getMessage());
        }
    }
}