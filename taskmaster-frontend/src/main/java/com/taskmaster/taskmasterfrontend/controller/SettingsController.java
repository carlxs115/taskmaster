package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.DateFormatManager;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.TimeFormatManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;

import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * SETTINGSCONTROLLER
 *
 * Controlador de la pantalla de ajustes.
 */
public class SettingsController {

    @FXML private RadioButton days7;
    @FXML private RadioButton days15;
    @FXML private RadioButton days30;
    @FXML private Label retentionStatusLabel;
    @FXML private RadioButton langEs;
    @FXML private RadioButton langEn;
    @FXML private Label languageStatusLabel;
    @FXML private RadioButton timeSystem;
    @FXML private RadioButton time24h;
    @FXML private RadioButton time12h;
    @FXML private Label timeFormatStatusLabel;
    @FXML private ComboBox<String> dateFormatCombo;
    @FXML private Label dateFormatStatusLabel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LanguageManager lm = LanguageManager.getInstance();

    @FXML
    public void initialize() {
        loadSettings();

        // Seleccionar idioma actual
        if (LanguageManager.getInstance().isSpanish()) {
            langEs.setSelected(true);
        } else {
            langEn.setSelected(true);
        }

        // Seleccionar formato de horas actual
        TimeFormatManager tfm = TimeFormatManager.getInstance();
        switch (tfm.getCurrentFormat()) {
            case H24    -> time24h.setSelected(true);
            case H12    -> time12h.setSelected(true);
            default     -> timeSystem.setSelected(true);
        }

        // Actualizar textos al cambiar idioma
        lm.bundleProperty().addListener((obs, oldBundle, newBundle) -> {
            // Los labels con %clave en FXML no se actualizan solos, pero el statusLabel sí necesita reset
            languageStatusLabel.setVisible(false);
            retentionStatusLabel.setVisible(false);
            timeFormatStatusLabel.setVisible(false);
        });

        // Poblar ComboBox de formato de fecha
        Locale locale = lm.getCurrentLocale();
        for (DateFormatManager.DateFormat fmt : DateFormatManager.DateFormat.values()) {
            dateFormatCombo.getItems().add(DateFormatManager.getLabel(fmt));
        }

        // Seleccionar el formato actual
        DateFormatManager dfm = DateFormatManager.getInstance();
        dateFormatCombo.getSelectionModel().select(
                dfm.getCurrentFormat().ordinal()
        );
    }

    private void loadSettings() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/settings");

                if (response.statusCode() == 200) {
                    JsonNode settings = objectMapper.readTree(response.body());
                    int days = settings.get("trashRetentionDays").asInt();

                    Platform.runLater(() -> {
                        if (days == 7) days7.setSelected(true);
                        else if (days == 15) days15.setSelected(true);
                        else days30.setSelected(true);
                    });
                }
            } catch (Exception e) {

            }
        }).start();
    }

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
