package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.DateFormatManager;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.TimeFormatManager;
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

/**
 * Controlador del componente de historial de actividad.
 *
 * <p>Muestra en una {@link TableView} los registros de actividad asociados
 * a una entidad (tarea, subtarea o proyecto). Soporta la carga encadenada
 * de logs de subtareas cuando se visualiza el detalle de una tarea,
 * y de tareas y sus subtareas cuando se visualiza el detalle de un proyecto.</p>
 *
 * @author Carlos
 */
public class ActivityLogSectionController {

    @FXML
    private TableView<ActivityRow> activityTable;
    @FXML private TableColumn<ActivityRow, String> colDate;
    @FXML private TableColumn<ActivityRow, String> colAction;
    @FXML private TableColumn<ActivityRow, String> colEntity;
    @FXML private TableColumn<ActivityRow, String> colDetail;

    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Traduce un código de acción del backend a su etiqueta localizada.
     *
     * @param actionType Código de acción (p.ej. {@code "TASK_CREATED"}).
     * @return Etiqueta localizada correspondiente, o el propio código si no hay traducción.
     */
    private String getActionLabel(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED"             -> lm.get("common.task.created");
            case "TASK_EDITED"              -> lm.get("common.task.edited");
            case "TASK_DELETED"             -> lm.get("actlog.action.task.deleted");
            case "TASK_PERMANENTLY_DELETED" -> lm.get("actlog.action.task.perm.deleted");
            case "TASK_RESTORED"            -> lm.get("actlog.action.task.restored");
            case "TASK_STATUS_CHANGED", "PROJECT_STATUS_CHANGED" -> lm.get("common.status.changed");
            case "SUBTASK_CREATED"          -> lm.get("common.subtask.created");
            case "SUBTASK_EDITED"           -> lm.get("common.subtask.edited");
            case "SUBTASK_DELETED"          -> lm.get("common.subtask.deleted");
            case "PROJECT_CREATED"          -> lm.get("common.project.created");
            case "PROJECT_EDITED"           -> lm.get("common.project.edited");
            case "PROJECT_DELETED"          -> lm.get("actlog.action.project.deleted");
            case "PROJECT_PERMANENTLY_DELETED" -> lm.get("actlog.action.project.perm.deleted");
            case "PROJECT_RESTORED"         -> lm.get("common.project.restored");
            case "PROFILE_UPDATED"          -> lm.get("common.profile.updated");
            case "PASSWORD_CHANGED"         -> lm.get("common.password.changed");
            default                         -> actionType;
        };
    }

    /**
     * Traduce un tipo de entidad del backend a su etiqueta localizada.
     *
     * @param entityType Tipo de entidad (p.ej. {@code "TASK"}, {@code "PROJECT"}).
     * @return Etiqueta localizada correspondiente, o el propio tipo si no hay traducción.
     */
    private String getEntityLabel(String entityType) {
        return switch (entityType) {
            case "TASK"    -> lm.get("common.task");
            case "SUBTASK" -> lm.get("actlog.entity.subtask");
            case "PROJECT" -> lm.get("common.project");
            case "PROFILE" -> lm.get("actlog.entity.profile");
            default        -> entityType;
        };
    }

    /**
     * Inicializa la tabla configurando la política de redimensionado
     * y los {@code cellValueFactory} de cada columna.
     */
    @FXML
    public void initialize() {
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Columnas de fecha y detalle: solo texto
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date()));
        colDetail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().detail()));

        // Columna Acción: texto con icono
        colAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));
        colAction.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                ActivityRow row = getTableView().getItems().get(getIndex());
                org.kordamp.ikonli.javafx.FontIcon icon =
                        new org.kordamp.ikonli.javafx.FontIcon(getActionIcon(row.actionType()));
                icon.setIconSize(12);
                icon.setIconColor(javafx.scene.paint.Color.web(getActionColor(row.actionType())));
                setGraphic(icon);
                setText(item);
                setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                setGraphicTextGap(8);
            }
        });

        // Columna Entidad: texto con icono
        colEntity.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().entity()));
        colEntity.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                ActivityRow row = getTableView().getItems().get(getIndex());
                org.kordamp.ikonli.javafx.FontIcon icon =
                        new org.kordamp.ikonli.javafx.FontIcon(getEntityIcon(row.entityType()));
                icon.setIconSize(11);
                icon.setIconColor(javafx.scene.paint.Color.web("#888888"));
                setGraphic(icon);
                setText(item);
                setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                setGraphicTextGap(6);
            }
        });
    }

    /**
     * Carga los registros de actividad para una entidad concreta.
     *
     * <p>Si {@code extraTypes} contiene {@code "SUBTASK"}, carga además los logs
     * de todas las subtareas de la tarea indicada. Si contiene {@code "TASK"},
     * carga los logs de todas las tareas del proyecto y sus respectivas subtareas.
     * Los registros se ordenan por fecha descendente.</p>
     *
     * @param entityType Tipo de entidad principal (p.ej. {@code "TASK"}).
     * @param entityId   Identificador de la entidad principal.
     * @param extraTypes Tipos adicionales para carga encadenada (opcional).
     */
    public void loadForEntity(String entityType, Long entityId, String... extraTypes) {
        try {
            var apiService = AppContext.getInstance().getApiService();
            List<ActivityRow> rows = new ArrayList<>();

            rows.addAll(fetchRows(apiService, entityType, entityId));

            if (extraTypes.length > 0 && "SUBTASK".equals(extraTypes[0])) {
                // Llamado desde TaskDetailController — cargar logs de subtareas de la tarea
                HttpResponse<String> subtasksResp = apiService.get("/api/tasks/" + entityId + "/subtasks/all");
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
                HttpResponse<String> tasksResp = apiService.get("/api/tasks/project/" + entityId + "/all");
                if (tasksResp != null && tasksResp.statusCode() == 200) {
                    JsonNode tasks = new com.fasterxml.jackson.databind.ObjectMapper()
                            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                            .readTree(tasksResp.body());
                    if (tasks.isArray()) {
                        for (JsonNode task : tasks) {
                            Long taskId = task.path("id").asLong();
                            rows.addAll(fetchRows(apiService, "TASK", taskId));
                            HttpResponse<String> subtasksResp = apiService.get("/api/tasks/" + taskId + "/subtasks/all");
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
            e.printStackTrace();
            activityTable.setItems(FXCollections.emptyObservableList());
        }
    }

    /**
     * Sobrecarga de {@link #loadForEntity(String, Long, String...)} sin tipos adicionales.
     *
     * @param entityType Tipo de entidad.
     * @param entityId   Identificador de la entidad.
     */
    public void loadForEntity(String entityType, Long entityId) {
        loadForEntity(entityType, entityId, new String[0]);
    }

    /**
     * Consulta la API y convierte la respuesta en una lista de {@link ActivityRow}.
     *
     * @param apiService Servicio HTTP del cliente.
     * @param entityType Tipo de entidad a consultar.
     * @param entityId   Identificador de la entidad.
     * @return Lista de filas de actividad obtenidas, o lista vacía si la llamada falla.
     * @throws Exception Si se produce un error al parsear la respuesta JSON.
     */
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

                rows.add(new ActivityRow(dateStr, actionLabel, entityLabel, detail, rawDate, actionType, entType));
            }
        }
        return rows;
    }

    /**
     * Construye el texto de detalle de un registro a partir de los valores
     * anterior y posterior al cambio.
     *
     * @param actionType Tipo de acción realizada.
     * @param oldVal     Valor anterior al cambio.
     * @param newVal     Valor posterior al cambio.
     * @param entityName Nombre de la entidad, usado como fallback.
     * @return Cadena descriptiva del cambio realizado.
     */
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

    /**
     * Indica si el valor dado corresponde a un estado de tarea.
     *
     * @param v Valor a comprobar.
     * @return {@code true} si es un valor de estado reconocido.
     */
    private boolean isStatusValue(String v) {
        return switch (v) { case "TODO","IN_PROGRESS","DONE","CANCELLED" -> true; default -> false; };
    }

    /**
     * Indica si el valor dado corresponde a una prioridad de tarea.
     *
     * @param v Valor a comprobar.
     * @return {@code true} si es un valor de prioridad reconocido.
     */
    private boolean isPriorityValue(String v) {
        return switch (v) { case "LOW","MEDIUM","HIGH","URGENT" -> true; default -> false; };
    }

    /**
     * Formatea una cadena de fecha ISO a la representación configurada
     * por el usuario (formato de fecha + formato de hora).
     *
     * @param raw Cadena de fecha en formato ISO ({@code LocalDateTime}).
     * @return Cadena formateada, o el valor original si el parseo falla.
     */
    private String formatDate(String raw) {
        try {
            LocalDateTime dt = LocalDateTime.parse(raw);
            DateTimeFormatter datePart = DateFormatManager.getInstance().getFormatter();
            DateTimeFormatter timePart = TimeFormatManager.getInstance().getFormatter();
            return dt.format(datePart) + " " + dt.format(timePart);
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * Traduce un valor de enum del backend (estado o prioridad) a su
     * etiqueta localizada.
     *
     * @param value Valor del enum (p.ej. {@code "TODO"}, {@code "HIGH"}).
     * @return Etiqueta localizada, o el valor original si no hay traducción.
     */
    private String translateValue(String value) {
        return switch (value) {
            case "TODO"        -> lm.get("status.todo");
            case "IN_PROGRESS" -> lm.get("status.inprogress");
            case "DONE"        -> lm.get("status.done");
            case "CANCELLED"   -> lm.get("status.cancelled");
            case "LOW"         -> lm.get("priority.low");
            case "MEDIUM"      -> lm.get("priority.medium");
            case "HIGH"        -> lm.get("priority.high");
            case "URGENT"      -> lm.get("priority.urgent");
            default            -> value;
        };
    }

    /**
     * Devuelve el identificador Ikonli del icono asociado al tipo de acción.
     *
     * @param actionType Código del tipo de acción.
     * @return Identificador del icono FontAwesome.
     */
    private String getActionIcon(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED", "SUBTASK_CREATED"        -> "fas-plus-circle";
            case "TASK_EDITED", "SUBTASK_EDITED"          -> "fas-pen";
            case "TASK_DELETED", "SUBTASK_DELETED"        -> "fas-trash";
            case "TASK_PERMANENTLY_DELETED"               -> "fas-times-circle";
            case "TASK_RESTORED"                          -> "fas-undo";
            case "TASK_STATUS_CHANGED"                    -> "fas-sync-alt";
            case "PROJECT_CREATED"                        -> "fas-folder-plus";
            case "PROJECT_EDITED"                         -> "fas-pen";
            case "PROJECT_DELETED"                        -> "fas-trash";
            case "PROJECT_PERMANENTLY_DELETED"            -> "fas-times-circle";
            case "PROJECT_RESTORED"                       -> "fas-undo";
            case "PROJECT_STATUS_CHANGED"                 -> "fas-sync-alt";
            case "PROFILE_UPDATED"                        -> "fas-user-edit";
            case "PASSWORD_CHANGED"                       -> "fas-key";
            default                                       -> "fas-circle";
        };
    }

    /**
     * Devuelve el color hex asociado al tipo de acción.
     *
     * @param actionType Código del tipo de acción.
     * @return Color en formato hex.
     */
    private String getActionColor(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED", "SUBTASK_CREATED", "PROJECT_CREATED"     -> "#22c55e";
            case "TASK_DELETED", "SUBTASK_DELETED", "PROJECT_DELETED"     -> "#f59e0b";
            case "TASK_PERMANENTLY_DELETED", "PROJECT_PERMANENTLY_DELETED" -> "#e74c3c";
            case "TASK_RESTORED", "PROJECT_RESTORED"                       -> "#3b82f6";
            case "TASK_STATUS_CHANGED", "PROJECT_STATUS_CHANGED"           -> "#7c3aed";
            default                                                         -> "#888888";
        };
    }

    /**
     * Devuelve el identificador Ikonli del icono asociado al tipo de entidad.
     *
     * @param entityType Tipo de entidad.
     * @return Identificador del icono FontAwesome.
     */
    private String getEntityIcon(String entityType) {
        return switch (entityType) {
            case "TASK"    -> "fas-tasks";
            case "SUBTASK" -> "fas-stream";
            case "PROJECT" -> "fas-folder";
            case "PROFILE" -> "fas-user";
            default        -> "fas-circle";
        };
    }

    /**
     * Registro que representa una fila de la tabla de historial de actividad.
     *
     * @param date       Fecha formateada para mostrar.
     * @param action     Etiqueta localizada de la acción realizada.
     * @param entity     Tipo e identificador de la entidad afectada.
     * @param detail     Descripción del cambio realizado.
     * @param rawDate    Fecha en formato ISO, usada para ordenación.
     * @param actionType Código original de la acción, usado para mapear iconos.
     * @param entityType Código original del tipo de entidad, usado para mapear iconos.
     */
    public record ActivityRow(String date, String action, String entity, String detail,
                              String rawDate, String actionType, String entityType) {}
}
