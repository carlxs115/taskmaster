package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

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
     * Datos de cada tema disponible.
     * Cada entrada contiene: enum del tema, nombre visible, color de acento,
     * color de fondo y color de acento secundario.
     */
    private static final Object[][] THEME_DATA = {
            { ThemeManager.Theme.AMATISTA,        "Amatista",      "#7c3aed", "#ffffff", "#a78bfa" },
            { ThemeManager.Theme.AMATISTA_DARK,   "Amatista Dark", "#a78bfa", "#13131f", "#7c3aed" },
            { ThemeManager.Theme.AURORA_BOREALIS, "Aurora Boreal", "#64FFDA", "#0B1426", "#82B1FF" },
            { ThemeManager.Theme.OCEANO,          "Océano",        "#3b82f6", "#0f2447", "#60a5fa" },
            { ThemeManager.Theme.PRADERA,         "Pradera",       "#10b981", "#ffffff", "#34d399" },
            { ThemeManager.Theme.AMBAR,           "Ámbar",         "#f59e0b", "#ffffff", "#fbbf24" },
            { ThemeManager.Theme.SAKURA,          "Sakura",        "#ec4899", "#ffffff", "#f472b6" },
            { ThemeManager.Theme.PERLA,           "Perla",         "#3b82f6", "#f0f4ff", "#60a5fa" },
            { ThemeManager.Theme.ARTICO,          "Ártico",        "#0ea5e9", "#f0f9ff", "#38bdf8" },
            { ThemeManager.Theme.NOCHE,           "Noche",         "#22d3ee", "#000000", "#67e8f9" },
            { ThemeManager.Theme.VIGILANTE,       "Vigilante",     "#facc15", "#080808", "#fde047" },
            { ThemeManager.Theme.HACKER,          "Hacker",        "#00ff41", "#000000", "#39ff6e" },
            { ThemeManager.Theme.LUZ,             "Luz",           "#ec4899", "#080808", "#f472b6" },
    };

    /**
     * Inicializa la pantalla cargando los ajustes del backend, preseleccionando
     * el idioma y el formato de hora actuales, configurando el combo de formato
     * de fecha y construyendo el selector visual de temas.
     */
    @FXML
    public void initialize() {
        loadSettings();

        // Idioma actual
        if (LanguageManager.getInstance().isSpanish()) {
            langEs.setSelected(true);
        } else {
            langEn.setSelected(true);
        }

        // Formato de horas actual
        TimeFormatManager tfm = TimeFormatManager.getInstance();
        switch (tfm.getCurrentFormat()) {
            case H24    -> time24h.setSelected(true);
            case H12    -> time12h.setSelected(true);
            default     -> timeSystem.setSelected(true);
        }

        // Listener de cambio de idioma
        lm.bundleProperty().addListener((obs, oldBundle, newBundle) -> {
            languageStatusLabel.setVisible(false);
            retentionStatusLabel.setVisible(false);
            timeFormatStatusLabel.setVisible(false);
        });

        // ComboBox de formato de fecha
        for (DateFormatManager.DateFormat fmt : DateFormatManager.DateFormat.values()) {
            dateFormatCombo.getItems().add(DateFormatManager.getLabel(fmt));
        }
        dateFormatCombo.getSelectionModel().select(
                DateFormatManager.getInstance().getCurrentFormat().ordinal());

        // Construir selector de temas
        buildThemeSelector();
    }

    /**
     * Construye las tarjetas de selección de tema en el {@link FlowPane}.
     * Cada tarjeta muestra el nombre del tema y dos círculos de color
     * (acento y fondo), o corazones en el caso del tema Luz.
     * La tarjeta del tema activo aparece resaltada con borde y sombra.
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
            card.setAlignment(javafx.geometry.Pos.CENTER);
            card.setPrefWidth(90);
            card.setPadding(new javafx.geometry.Insets(10, 8, 10, 8));
            card.setStyle(buildCardStyle(bg, accent, isSelected));
            card.setUserData(theme);
            card.setCursor(javafx.scene.Cursor.HAND);

            // Preview: tres puntos o tres corazones para Luz
            HBox dots = new HBox(4);
            dots.setAlignment(javafx.geometry.Pos.CENTER);
            String[] dotColors = { accent, accentLight };

            for (String dotColor : dotColors) {
                if (isLuz) {
                    Label heart = new Label("♥");
                    heart.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 14px;");
                    dots.getChildren().add(heart);
                } else {
                    Circle dot = new Circle(7);
                    dot.setFill(javafx.scene.paint.Color.web(dotColor));
                    dots.getChildren().add(dot);
                }
            }

            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; " +
                    "-fx-text-fill: " + (isDarkBg(bg) ? "#ffffff" : "#2d2d2d") + "; " +
                    "-fx-wrap-text: true; -fx-text-alignment: center;");
            nameLabel.setMaxWidth(80);
            nameLabel.setAlignment(javafx.geometry.Pos.CENTER);

            card.getChildren().addAll(dots, nameLabel);
            card.setOnMouseClicked(e -> handleThemeSelected(theme, card));
            themesContainer.getChildren().add(card);
        }
    }

    /**
     * Genera el estilo CSS inline de una tarjeta de tema.
     *
     * @param bg       Color de fondo del tema en formato hex.
     * @param accent   Color de acento del tema en formato hex.
     * @param selected {@code true} si la tarjeta corresponde al tema activo.
     * @return Cadena de estilo CSS con borde, radio, fondo y sombra opcional.
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
     * Determina si un color de fondo es oscuro para elegir el color
     * del texto de la tarjeta (blanco sobre oscuro, gris sobre claro).
     *
     * @param hex Color en formato hex.
     * @return {@code true} si la luminancia calculada es inferior a 0.5.
     */
    private boolean isDarkBg(String hex) {
        try {
            javafx.scene.paint.Color c = javafx.scene.paint.Color.web(hex);
            double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
            return luminance < 0.5;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Aplica el tema seleccionado visualmente, actualiza el estilo de todas
     * las tarjetas y guarda el tema en el backend en un hilo secundario.
     *
     * @param theme        Tema seleccionado por el usuario.
     * @param selectedCard Tarjeta pulsada, usada para actualizar los estilos.
     */
    private void handleThemeSelected(ThemeManager.Theme theme, VBox selectedCard) {
        ThemeManager.getInstance().applyTheme(theme);

        for (var node : themesContainer.getChildren()) {
            if (node instanceof VBox card) {
                ThemeManager.Theme cardTheme = (ThemeManager.Theme) card.getUserData();
                for (Object[] data : THEME_DATA) {
                    if (data[0] == cardTheme) {
                        boolean sel = card == selectedCard;
                        card.setStyle(buildCardStyle((String) data[3], (String) data[2], sel));
                        break;
                    }
                }
            }
        }

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .patch("/api/settings/theme?theme=" + theme.name(), null);
                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        themeStatusLabel.setText(lm.get("settings.saved"));
                        themeStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2ecc71;");
                        themeStatusLabel.setVisible(true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    themeStatusLabel.setText(lm.get("settings.connection.error"));
                    themeStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e74c3c;");
                    themeStatusLabel.setVisible(true);
                });
            }
        }).start();
    }

    /**
     * Obtiene los ajustes del usuario desde el backend, preselecciona los días
     * de retención de la papelera y reconstruye el selector de temas con el
     * tema guardado ya marcado.
     */
    private void loadSettings() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/settings");
                if (response.statusCode() == 200) {
                    JsonNode settings = objectMapper.readTree(response.body());
                    int days = settings.get("trashRetentionDays").asInt();

                    // Cargar tema guardado
                    String themeName = settings.has("theme")
                            ? settings.get("theme").asText()
                            : "AMATISTA";
                    ThemeManager.Theme savedTheme = ThemeManager.fromString(themeName);

                    Platform.runLater(() -> {
                        if (days == 7) days7.setSelected(true);
                        else if (days == 15) days15.setSelected(true);
                        else days30.setSelected(true);

                        ThemeManager.getInstance().applyTheme(savedTheme);
                        buildThemeSelector();
                    });
                }
            } catch (Exception e) {}
        }).start();
    }

    /**
     * Aplica el formato de fecha seleccionado en el combo al {@link DateFormatManager}
     * y muestra la confirmación de guardado.
     */
    @FXML
    private void handleSaveDateFormat() {
        int idx = dateFormatCombo.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;
        DateFormatManager.DateFormat[] values = DateFormatManager.DateFormat.values();
        DateFormatManager.getInstance().setFormat(values[idx]);

        dateFormatStatusLabel.setText(lm.get("settings.saved"));
        dateFormatStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #2ecc71;");
        dateFormatStatusLabel.setVisible(true);
    }

    /**
     * Aplica el formato de hora seleccionado con los radio buttons al
     * {@link TimeFormatManager} y muestra la confirmación de guardado.
     */
    @FXML
    private void handleSaveTimeFormat() {
        TimeFormatManager tfm = TimeFormatManager.getInstance();
        if (time24h.isSelected())     tfm.setFormat(TimeFormatManager.TimeFormat.H24);
        else if (time12h.isSelected()) tfm.setFormat(TimeFormatManager.TimeFormat.H12);
        else                           tfm.setFormat(TimeFormatManager.TimeFormat.SYSTEM);

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

        new Thread(() -> {
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
                Platform.runLater(() -> {
                    retentionStatusLabel.setText(lm.get("settings.connection.error"));
                    retentionStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    retentionStatusLabel.setVisible(true);
                });
            }
        }).start();
    }

    /**
     * Aplica el idioma seleccionado, lo persiste localmente y recarga
     * la vista de ajustes tras una breve pausa para que el mensaje de
     * confirmación sea visible.
     */
    @FXML
    private void handleSaveLanguage() {
        Locale locale = langEs.isSelected() ? new Locale("es") : Locale.ENGLISH;
        lm.setLocale(locale);
        lm.saveLocalePreference(locale);

        languageStatusLabel.setText(lm.get("settings.saved"));
        languageStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #f59e0b;");
        languageStatusLabel.setVisible(true);

        // Esperar 500ms para que el mensaje sea visible antes de recargar la vista
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(500)
        );
        pause.setOnFinished(e -> reloadThisView());
        pause.play();
    }

    /**
     * Recarga la vista de ajustes en el idioma actualizado reemplazando
     * el nodo actual en el layout del controlador principal.
     */
    private void reloadThisView() {
        try {
            javafx.scene.Node currentView = retentionStatusLabel.getScene()
                    .lookup("#settingsRoot"); // añadiremos fx:id abajo
            javafx.scene.layout.HBox parent =
                    (javafx.scene.layout.HBox) currentView.getParent();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/taskmaster/taskmasterfrontend/settings-view.fxml"),
                    lm.getBundle()
            );
            javafx.scene.layout.VBox newView = loader.load();
            javafx.scene.layout.HBox.setHgrow(newView, javafx.scene.layout.Priority.ALWAYS);
            newView.setUserData("settings");

            int idx = parent.getChildren().indexOf(currentView);
            parent.getChildren().set(idx, newView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
