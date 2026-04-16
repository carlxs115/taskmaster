package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class AddWorkLogController {

    @FXML private DatePicker datePicker;
    @FXML private TextField hoursField;
    @FXML private ComboBox<String> activityTypeCombo;
    @FXML private TextArea noteField;
    @FXML private Label errorLabel;

    private Long taskId;
    private Long editingLogId = null;
    private Runnable onWorkLogAdded;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> ACTIVITY_TYPES = new LinkedHashMap<>();
    static {
        ACTIVITY_TYPES.put("Análisis",       "ANALYSIS");
        ACTIVITY_TYPES.put("Diseño",         "DESIGN");
        ACTIVITY_TYPES.put("Desarrollo",     "DEVELOPMENT");
        ACTIVITY_TYPES.put("Test",           "TEST");
        ACTIVITY_TYPES.put("Instalación",    "INSTALLATION");
        ACTIVITY_TYPES.put("Documentación",  "DOCUMENTATION");
        ACTIVITY_TYPES.put("Reunión",        "MEETING");
        ACTIVITY_TYPES.put("Soporte",        "SUPPORT");
        ACTIVITY_TYPES.put("Gestión",        "MANAGEMENT");
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setOnWorkLogAdded(Runnable callback) {
        this.onWorkLogAdded = callback;
    }

    @FXML
    public void initialize() {
        activityTypeCombo.setItems(FXCollections.observableArrayList(ACTIVITY_TYPES.keySet()));
        activityTypeCombo.setValue("Desarrollo");
        datePicker.setValue(LocalDate.now());

        // Solo permitir números y punto en hoursField
        hoursField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                hoursField.setText(oldVal);
            }
        });

        Platform.runLater(() -> datePicker.getParent().requestFocus());
    }

    public void initData(Long logId, JsonNode log) {
        this.editingLogId = logId;
        datePicker.setValue(java.time.LocalDate.parse(log.path("date").asText().substring(0, 10)));
        hoursField.setText(log.path("hours").asText());
        String note = log.path("note").isNull() ? "" : log.path("note").asText();
        noteField.setText(note);

        // Buscar el label del tipo en el mapa
        String backendType = log.path("activityType").asText();
        ACTIVITY_TYPES.entrySet().stream()
                .filter(e -> e.getValue().equals(backendType))
                .findFirst()
                .ifPresent(e -> activityTypeCombo.setValue(e.getKey()));
    }

    @FXML
    private void handleSave() {
        if (!validate()) return;
        hideError();

        BigDecimal hours = new BigDecimal(hoursField.getText().trim());
        String activityType = ACTIVITY_TYPES.get(activityTypeCombo.getValue());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", datePicker.getValue().toString());
        body.put("activityType", activityType);
        body.put("hours", hours);
        body.put("note", noteField.getText().trim());

        String endpoint = editingLogId == null
                ? "/api/worklogs/task/" + taskId
                : "/api/worklogs/" + editingLogId;

        new Thread(() -> {
            try {
                HttpResponse<String> response = editingLogId == null
                        ? AppContext.getInstance().getApiService().postWithAuth(endpoint, body)
                        : AppContext.getInstance().getApiService().put(endpoint, body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 201 || response.statusCode() == 200) {
                        if (onWorkLogAdded != null) onWorkLogAdded.run();
                        closeDialog();
                    } else {
                        showError("Error al guardar el registro");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error de conexión con el servidor"));
            }
        }).start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private boolean validate() {
        if (datePicker.getValue() == null) {
            showError("La fecha es obligatoria");
            return false;
        }
        String hoursText = hoursField.getText().trim();
        if (hoursText.isEmpty()) {
            showError("Las horas son obligatorias");
            return false;
        }
        try {
            BigDecimal h = new BigDecimal(hoursText);
            if (h.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Las horas deben ser mayor que 0");
                return false;
            }
        } catch (NumberFormatException e) {
            showError("Introduce un número válido de horas");
            return false;
        }
        if (activityTypeCombo.getValue() == null) {
            showError("El tipo de actividad es obligatorio");
            return false;
        }
        return true;
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void closeDialog() {
        ((Stage) datePicker.getScene().getWindow()).close();
    }
}
