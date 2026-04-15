package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActivityLogSectionController {

    @FXML
    private TableView<ActivityRow> activityTable;
    @FXML private TableColumn<ActivityRow, String> colDate;
    @FXML private TableColumn<ActivityRow, String> colAction;
    @FXML private TableColumn<ActivityRow, String> colEntity;
    @FXML private TableColumn<ActivityRow, String> colDetail;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("TASK_CREATED",             "Tarea creada"),
            Map.entry("TASK_EDITED",              "Tarea editada"),
            Map.entry("TASK_DELETED",             "Enviada a papelera"),
            Map.entry("TASK_PERMANENTLY_DELETED", "Eliminada definitivamente"),
            Map.entry("TASK_RESTORED",            "Restaurada"),
            Map.entry("TASK_STATUS_CHANGED",      "Estado cambiado"),
            Map.entry("SUBTASK_CREATED",          "Subtarea creada"),
            Map.entry("SUBTASK_EDITED",           "Subtarea editada"),
            Map.entry("SUBTASK_DELETED",          "Subtarea eliminada"),
            Map.entry("PROJECT_CREATED",          "Proyecto creado"),
            Map.entry("PROJECT_EDITED",           "Proyecto editado"),
            Map.entry("PROJECT_DELETED",          "Enviado a papelera"),
            Map.entry("PROJECT_PERMANENTLY_DELETED", "Eliminado definitivamente"),
            Map.entry("PROJECT_RESTORED",         "Proyecto restaurado"),
            Map.entry("PROJECT_STATUS_CHANGED",   "Estado cambiado"),
            Map.entry("PROFILE_UPDATED",          "Perfil actualizado"),
            Map.entry("PASSWORD_CHANGED",         "Contraseña cambiada")
    );

    private static final Map<String, String> ENTITY_LABELS = Map.of(
            "TASK",    "Tarea",
            "SUBTASK", "Subtarea",
            "PROJECT", "Proyecto",
            "PROFILE", "Perfil"
    );

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date()));
        colAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));
        colEntity.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().entity()));
        colDetail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().detail()));
    }

    public void loadForEntity(String entityType, Long entityId, String... extraTypes) {
        try {
            var apiService = AppContext.getInstance().getApiService();
            List<ActivityRow> rows = new ArrayList<>();

            rows.addAll(fetchRows(apiService, entityType, entityId));

            if (extraTypes.length > 0 && "SUBTASK".equals(extraTypes[0])) {
                // Llamado desde TaskDetailController — cargar logs de subtareas de la tarea
                HttpResponse<String> subtasksResp = apiService.get("/api/tasks/" + entityId + "/subtasks");
                if (subtasksResp != null && subtasksResp.statusCode() == 200) {
                    JsonNode subtasks = new com.fasterxml.jackson.databind.ObjectMapper()
                            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                            .readTree(subtasksResp.body());
                    if (subtasks.isArray()) {
                        for (JsonNode sub : subtasks) {
                            Long subId = sub.path("id").asLong();
                            rows.addAll(fetchRows(apiService, "SUBTASK", subId));
                        }
                    }
                }
            } else if (extraTypes.length > 0 && "TASK".equals(extraTypes[0])) {
                // Llamado desde ProjectDetailController — cargar logs de tareas y sus subtareas
                HttpResponse<String> tasksResp = apiService.get("/api/tasks?projectId=" + entityId);
                if (tasksResp != null && tasksResp.statusCode() == 200) {
                    JsonNode tasks = new com.fasterxml.jackson.databind.ObjectMapper()
                            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                            .readTree(tasksResp.body());
                    if (tasks.isArray()) {
                        for (JsonNode task : tasks) {
                            Long taskId = task.path("id").asLong();
                            rows.addAll(fetchRows(apiService, "TASK", taskId));
                            HttpResponse<String> subtasksResp = apiService.get("/api/tasks/" + taskId + "/subtasks");
                            if (subtasksResp != null && subtasksResp.statusCode() == 200) {
                                JsonNode subtasks = new com.fasterxml.jackson.databind.ObjectMapper()
                                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                        .readTree(subtasksResp.body());
                                if (subtasks.isArray()) {
                                    for (JsonNode sub : subtasks) {
                                        Long subId = sub.path("id").asLong();
                                        rows.addAll(fetchRows(apiService, "SUBTASK", subId));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            rows.sort((a, b) -> b.rawDate().compareTo(a.rawDate()));
            activityTable.setItems(FXCollections.observableArrayList(rows));
        } catch (Exception e) {
            System.out.println("ERROR en loadForEntity: " + e.getMessage());
            e.printStackTrace();
            activityTable.setItems(FXCollections.emptyObservableList());
        }
    }

    // Sobrecarga sin extraTypes — ProjectDetailController sigue igual
    public void loadForEntity(String entityType, Long entityId) {
        loadForEntity(entityType, entityId, new String[0]);
    }

    private List<ActivityRow> fetchRows(
            com.taskmaster.taskmasterfrontend.service.ApiService apiService,
            String entityType, Long entityId) throws Exception {

        HttpResponse<String> httpResponse = apiService.get(
                "/api/activity-log/entity?entityType=" + entityType + "&entityId=" + entityId);

        List<ActivityRow> rows = new ArrayList<>();
        if (httpResponse == null || httpResponse.statusCode() != 200) return rows;

        JsonNode response = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .readTree(httpResponse.body());

        if (response != null && response.isArray()) {
            for (JsonNode node : response) {
                String actionType = node.path("actionType").asText();
                String entType    = node.path("entityType").asText();
                String rawDate    = node.path("createdAt").asText();
                String oldVal     = node.path("oldValue").asText("");
                String newVal     = node.path("newValue").asText("");
                String entityName = node.path("entityName").asText("");

                String dateStr     = formatDate(rawDate);
                String actionLabel = ACTION_LABELS.getOrDefault(actionType, actionType);
                String entityId2   = node.path("entityId").asText("");
                String entityLabel = (!entityId2.isBlank() ? "#" + entityId2 + " " : "")
                        + ENTITY_LABELS.getOrDefault(entType, entType);
                String detail      = buildDetail(actionType, oldVal, newVal, entityName);

                rows.add(new ActivityRow(dateStr, actionLabel, entityLabel, detail, rawDate));
            }
        }
        return rows;
    }

    private String buildDetail(String actionType, String oldVal, String newVal, String entityName) {
        if (!oldVal.isBlank() && !newVal.isBlank()) {
            String old = translateValue(oldVal);
            String nw  = translateValue(newVal);

            if (STATUS_LABELS.containsKey(oldVal) || STATUS_LABELS.containsKey(newVal)) {
                return "Estado: " + old + " → " + nw;
            }
            if (PRIORITY_LABELS.containsKey(oldVal) || PRIORITY_LABELS.containsKey(newVal)) {
                return "Prioridad: " + old + " → " + nw;
            }
            if (oldVal.matches("\\d{2}/\\d{2}/\\d{4}") || newVal.matches("\\d{2}/\\d{2}/\\d{4}")
                    || oldVal.equals("Sin fecha") || newVal.equals("Sin fecha")) {
                return "Fecha límite: " + old + " → " + nw;
            }
            return old + " → " + nw;
        }
        if (!newVal.isBlank()) return translateValue(newVal);
        if (!oldVal.isBlank()) return translateValue(oldVal);
        return entityName;
    }

    private String formatDate(String raw) {
        try {
            return LocalDateTime.parse(raw).format(FORMATTER);
        } catch (Exception e) {
            return raw;
        }
    }

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "TODO",        "Pendiente",
            "IN_PROGRESS", "En curso",
            "DONE",        "Completada",
            "CANCELLED",   "Cancelada"
    );

    private static final Map<String, String> PRIORITY_LABELS = Map.of(
            "LOW",    "Baja",
            "MEDIUM", "Media",
            "HIGH",   "Alta",
            "URGENT", "Urgente"
    );

    private String translateValue(String value) {
        if (STATUS_LABELS.containsKey(value))   return STATUS_LABELS.get(value);
        if (PRIORITY_LABELS.containsKey(value)) return PRIORITY_LABELS.get(value);
        return value;
    }

    public record ActivityRow(String date, String action, String entity, String detail, String rawDate) {}
}
