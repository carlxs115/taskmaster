package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
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
    private final LanguageManager lm = LanguageManager.getInstance();

    private Map<String, String> getActivityTypes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(lm.get("activity.ANALYSIS"),      "ANALYSIS");
        map.put(lm.get("activity.DESIGN"),        "DESIGN");
        map.put(lm.get("activity.DEVELOPMENT"),   "DEVELOPMENT");
        map.put(lm.get("activity.TEST"),          "TEST");
        map.put(lm.get("activity.INSTALLATION"),  "INSTALLATION");
        map.put(lm.get("activity.DOCUMENTATION"), "DOCUMENTATION");
        map.put(lm.get("activity.MEETING"),       "MEETING");
        map.put(lm.get("activity.SUPPORT"),       "SUPPORT");
        map.put(lm.get("activity.MANAGEMENT"),    "MANAGEMENT");
        return map;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setOnWorkLogAdded(Runnable callback) {
        this.onWorkLogAdded = callback;
    }

    @FXML
    public void initialize() {
        Map<String, String> types = getActivityTypes();
        activityTypeCombo.setItems(FXCollections.observableArrayList(types.keySet()));
        activityTypeCombo.setValue(lm.get("activity.DEVELOPMENT"));
        datePicker.setValue(LocalDate.now());
        hoursField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                hoursField.setText(oldVal);
            }
        });
        Platform.runLater(() -> datePicker.getParent().requestFocus());
    }

    public void initData(Long logId, JsonNode log) {
        this.editingLogId = logId;
        datePicker.setValue(LocalDate.parse(log.path("date").asText().substring(0, 10)));
        hoursField.setText(log.path("hours").asText());
        noteField.setText(log.path("note").isNull() ? "" : log.path("note").asText());
        String backendType = log.path("activityType").asText();
        Map<String, String> types = getActivityTypes();
        types.entrySet().stream()
                .filter(e -> e.getValue().equals(backendType))
                .findFirst()
                .ifPresent(e -> activityTypeCombo.setValue(e.getKey()));
    }

    @FXML
    private void handleSave() {
        if (!validate()) return;
        hideError();

        BigDecimal hours = new BigDecimal(hoursField.getText().trim());
        String activityType = getActivityTypes().get(activityTypeCombo.getValue());

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
                        showError(lm.get("worklog.error.save"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private boolean validate() {
        if (datePicker.getValue() == null) {
            showError(lm.get("worklog.error.date"));
            return false;
        }
        String hoursText = hoursField.getText().trim();
        if (hoursText.isEmpty()) {
            showError(lm.get("worklog.error.hours.empty"));
            return false;
        }
        try {
            BigDecimal h = new BigDecimal(hoursText);
            if (h.compareTo(BigDecimal.ZERO) <= 0) {
                showError(lm.get("worklog.error.hours.zero"));
                return false;
            }
        } catch (NumberFormatException e) {
            showError(lm.get("worklog.error.hours.invalid"));
            return false;
        }
        if (activityTypeCombo.getValue() == null) {
            showError(lm.get("worklog.error.activity"));
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
