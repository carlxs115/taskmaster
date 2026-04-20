package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
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
    }

    private void applyLanguageToSettings() {
        ResourceBundle b = LanguageManager.getInstance().getBundle();
        // Los textos fijos del settings se actualizan aquí cuando hagamos el refactor completo
    }
}
