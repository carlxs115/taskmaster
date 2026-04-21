package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
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

    private final LanguageManager lm = LanguageManager.getInstance();
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String getActionLabel(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED"             -> lm.get("actlog.action.task.created");
            case "TASK_EDITED"              -> lm.get("actlog.action.task.edited");
            case "TASK_DELETED"             -> lm.get("actlog.action.task.deleted");
            case "TASK_PERMANENTLY_DELETED" -> lm.get("actlog.action.task.perm.deleted");
            case "TASK_RESTORED"            -> lm.get("actlog.action.task.restored");
            case "TASK_STATUS_CHANGED"      -> lm.get("actlog.action.task.status");
            case "SUBTASK_CREATED"          -> lm.get("actlog.action.subtask.created");
            case "SUBTASK_EDITED"           -> lm.get("actlog.action.subtask.edited");
            case "SUBTASK_DELETED"          -> lm.get("actlog.action.subtask.deleted");
            case "PROJECT_CREATED"          -> lm.get("actlog.action.project.created");
            case "PROJECT_EDITED"           -> lm.get("actlog.action.project.edited");
            case "PROJECT_DELETED"          -> lm.get("actlog.action.project.deleted");
            case "PROJECT_PERMANENTLY_DELETED" -> lm.get("actlog.action.project.perm.deleted");
            case "PROJECT_RESTORED"         -> lm.get("actlog.action.project.restored");
            case "PROJECT_STATUS_CHANGED"   -> lm.get("actlog.action.project.status");
            case "PROFILE_UPDATED"          -> lm.get("actlog.action.profile.updated");
            case "PASSWORD_CHANGED"         -> lm.get("actlog.action.password.changed");
            default                         -> actionType;
        };
    }

    private String getEntityLabel(String entityType) {
        return switch (entityType) {
            case "TASK"    -> lm.get("actlog.entity.task");
            case "SUBTASK" -> lm.get("actlog.entity.subtask");
            case "PROJECT" -> lm.get("actlog.entity.project");
            case "PROFILE" -> lm.get("actlog.entity.profile");
            default        -> entityType;
        };
    }

    @FXML
    public void initialize() {
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
                String actionLabel = getActionLabel(actionType);
                String entityId2   = node.path("entityId").asText("");
                String entityLabel = (!entityId2.isBlank() ? "#" + entityId2 + " " : "")
                        + getEntityLabel(entType);
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
            boolean isStatus   = isStatusValue(oldVal) || isStatusValue(newVal);
            boolean isPriority = isPriorityValue(oldVal) || isPriorityValue(newVal);
            boolean isDate     = oldVal.matches("\\d{2}/\\d{2}/\\d{4}")
                    || newVal.matches("\\d{2}/\\d{2}/\\d{4}")
                    || oldVal.equals("Sin fecha") || newVal.equals("Sin fecha");
            if (isStatus)   return java.text.MessageFormat.format(lm.get("actlog.detail.status"),   old, nw);
            if (isPriority) return java.text.MessageFormat.format(lm.get("actlog.detail.priority"), old, nw);
            if (isDate)     return java.text.MessageFormat.format(lm.get("actlog.detail.duedate"),  old, nw);
            return old + " → " + nw;
        }
        if (!newVal.isBlank()) return translateValue(newVal);
        if (!oldVal.isBlank()) return translateValue(oldVal);
        return entityName;
    }

    private boolean isStatusValue(String v) {
        return switch (v) { case "TODO","IN_PROGRESS","DONE","CANCELLED" -> true; default -> false; };
    }

    private boolean isPriorityValue(String v) {
        return switch (v) { case "LOW","MEDIUM","HIGH","URGENT" -> true; default -> false; };
    }

    private String formatDate(String raw) {
        try {
            return LocalDateTime.parse(raw).format(FORMATTER);
        } catch (Exception e) {
            return raw;
        }
    }

    private String translateValue(String value) {
        return switch (value) {
            case "TODO"        -> lm.get("status.translate.TODO");
            case "IN_PROGRESS" -> lm.get("status.translate.IN_PROGRESS");
            case "DONE"        -> lm.get("status.translate.DONE");
            case "CANCELLED"   -> lm.get("status.translate.CANCELLED");
            case "LOW"         -> lm.get("priority.low.label");
            case "MEDIUM"      -> lm.get("priority.medium.label");
            case "HIGH"        -> lm.get("priority.high.label");
            case "URGENT"      -> lm.get("priority.urgent.label");
            default            -> value;
        };
    }

    public record ActivityRow(String date, String action, String entity, String detail, String rawDate) {}
}
