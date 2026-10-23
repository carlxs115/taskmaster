package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.service.ApiService;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.DateFormatManager;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.TimeFormatManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador del componente de historial de actividad.
 *
 * <p>Muestra en una {@link TableView} los registros de actividad asociados
 * a una entidad (tarea, subtarea o proyecto). Soporta la carga encadenada
 * de logs de subtareas cuando se visualiza el detalle de una tarea,
 * y de tareas y sus subtareas cuando se visualiza el detalle de un proyecto.</p>
 *
 * <p>Utiliza los endpoints batch {@code /api/activity-log/entities} y
 * {@code /api/tasks/project/{id}/subtask-ids} para reducir el número de
 * llamadas HTTP de O(N×M) a O(1) independientemente del tamaño del proyecto.</p>
 *
 * @author Carlos
 */
public class ActivityLogSectionController {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogSectionController.class);

    @FXML private TableView<ActivityRow> activityTable;
    @FXML private TableColumn<ActivityRow, String> colDate;
    @FXML private TableColumn<ActivityRow, String> colAction;
    @FXML private TableColumn<ActivityRow, String> colEntity;
    @FXML private TableColumn<ActivityRow, String> colDetail;

    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * ObjectMapper reutilizable para parsear respuestas JSON.
     * Se inicializa una sola vez porque es thread-safe y caro de crear.
     */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Inicializa la tabla configurando la política de redimensionado
     * y los {@code cellValueFactory} de cada columna.
     */
    @FXML
    public void initialize() {
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Columnas de fecha y detalle: solo texto
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date()));
        colDetail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().detail()));

        // Columna Acción: texto con icono
        colAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));
        colAction.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                ActivityRow row = getTableView().getItems().get(getIndex());
                FontIcon icon = new FontIcon(getActionIcon(row.actionType()));
                icon.setIconSize(12);
                icon.setIconColor(Color.web(getActionColor(row.actionType())));
                setGraphic(icon);
                setText(item);
                setContentDisplay(ContentDisplay.LEFT);
                setGraphicTextGap(8);
            }
        });

        // Columna Entidad: texto con icono
        colEntity.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().entity()));
        colEntity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                ActivityRow row = getTableView().getItems().get(getIndex());
                FontIcon icon = new FontIcon(getEntityIcon(row.entityType()));
                icon.setIconSize(11);
                icon.setIconColor(Color.web("#888888"));
                setGraphic(icon);
                setText(item);
                setContentDisplay(ContentDisplay.LEFT);
                setGraphicTextGap(6);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Carga de datos
    // -------------------------------------------------------------------------

    /**
     * Carga los registros de actividad para una entidad concreta.
     *
     * <p>Si {@code extraTypes} contiene {@code "SUBTASK"}, carga además los logs
     * de todas las subtareas de la tarea indicada usando el endpoint batch.
     * Si contiene {@code "TASK"}, carga los logs de todas las tareas del proyecto
     * y sus subtareas usando los endpoints batch para minimizar las llamadas HTTP.
     * Los registros se ordenan por fecha descendente.</p>
     *
     * @param entityType tipo de entidad principal (p.ej. {@code "TASK"})
     * @param entityId   identificador de la entidad principal
     * @param extraTypes tipos adicionales para carga encadenada (opcional)
     */
    public void loadForEntity(String entityType, Long entityId, String... extraTypes) {
        try {
            ApiService apiService = AppContext.getInstance().getApiService();
            List<ActivityRow> rows = new ArrayList<>(fetchRows(apiService, entityType, entityId));

            if (extraTypes.length > 0 && "SUBTASK".equals(extraTypes[0])) {
                rows.addAll(fetchSubtaskRows(apiService, entityId));
            } else if (extraTypes.length > 0 && "TASK".equals(extraTypes[0])) {
                rows.addAll(fetchProjectTaskRows(apiService, entityId));
            }

            // Ordenamos todos los registros por fecha descendente
            rows.sort((a, b) -> b.rawDate().compareTo(a.rawDate()));
            activityTable.setItems(FXCollections.observableArrayList(rows));

        } catch (Exception e) {
            log.error("Error al cargar el historial de actividad: {}", e.getMessage());
            activityTable.setItems(FXCollections.emptyObservableList());
        }
    }

    /**
     * Sobrecarga de {@link #loadForEntity(String, Long, String...)} sin tipos adicionales.
     *
     * @param entityType tipo de entidad
     * @param entityId   identificador de la entidad
     */
    public void loadForEntity(String entityType, Long entityId) {
        loadForEntity(entityType, entityId, new String[0]);
    }

    // -------------------------------------------------------------------------
    // Métodos privados de carga — versión optimizada (batch)
    // -------------------------------------------------------------------------

    /**
     * Carga los logs de todas las subtareas de una tarea en dos llamadas HTTP:
     * una para obtener los IDs de subtareas y otra batch para sus logs.
     * Reemplaza el bucle N+1 anterior (una llamada por subtarea).
     *
     * @param apiService servicio HTTP
     * @param taskId     identificador de la tarea padre
     * @return lista de filas de actividad de las subtareas
     * @throws Exception si se produce un error de red o parseo
     */
    private List<ActivityRow> fetchSubtaskRows(ApiService apiService, Long taskId)
            throws Exception {
        HttpResponse<String> resp = apiService.get("/api/tasks/" + taskId + "/subtasks/all");
        if (resp == null || resp.statusCode() != 200) return List.of();

        JsonNode subtasks = objectMapper.readTree(resp.body());
        if (!subtasks.isArray() || subtasks.isEmpty()) return List.of();

        List<Long> subtaskIds = new ArrayList<>();
        for (JsonNode sub : subtasks) {
            subtaskIds.add(sub.path("id").asLong());
        }

        // Una sola llamada batch en lugar de N llamadas individuales
        return fetchRowsBatch(apiService, "SUBTASK", subtaskIds);
    }

    /**
     * Carga los logs de todas las tareas de un proyecto y sus subtareas
     * usando exactamente 4 llamadas HTTP, independientemente del tamaño del proyecto:
     * <ol>
     *   <li>GET /api/tasks/project/{id}/all → IDs de tareas raíz</li>
     *   <li>GET /api/activity-log/entities?entityType=TASK&entityIds=... → logs de tareas (batch)</li>
     *   <li>GET /api/tasks/project/{id}/subtask-ids → IDs de todas las subtareas</li>
     *   <li>GET /api/activity-log/entities?entityType=SUBTASK&entityIds=... → logs de subtareas (batch)</li>
     * </ol>
     *
     * @param apiService servicio HTTP
     * @param projectId  identificador del proyecto
     * @return lista de filas de actividad de tareas y subtareas
     * @throws Exception si se produce un error de red o parseo
     */
    private List<ActivityRow> fetchProjectTaskRows(ApiService apiService, Long projectId)
            throws Exception {
        // 1. Obtener todas las tareas raíz del proyecto
        HttpResponse<String> taskResp = apiService.get("/api/tasks/project/" + projectId + "/all");
        if (taskResp == null || taskResp.statusCode() != 200) return List.of();

        JsonNode tasks = objectMapper.readTree(taskResp.body());
        if (!tasks.isArray() || tasks.isEmpty()) return List.of();

        List<Long> taskIds = new ArrayList<>();
        for (JsonNode task : tasks) {
            taskIds.add(task.path("id").asLong());
        }

        List<ActivityRow> rows = new ArrayList<>();

        // 2. Logs de todas las tareas en una sola llamada batch
        rows.addAll(fetchRowsBatch(apiService, "TASK", taskIds));

        // 3. IDs de todas las subtareas del proyecto en una sola llamada
        HttpResponse<String> subtaskIdResp = apiService.get(
                "/api/tasks/project/" + projectId + "/subtask-ids");
        if (subtaskIdResp != null && subtaskIdResp.statusCode() == 200) {
            JsonNode subtaskIdArray = objectMapper.readTree(subtaskIdResp.body());
            if (subtaskIdArray.isArray() && !subtaskIdArray.isEmpty()) {
                List<Long> subtaskIds = new ArrayList<>();
                for (JsonNode idNode : subtaskIdArray) {
                    subtaskIds.add(idNode.asLong());
                }

                // 4. Logs de todas las subtareas en una sola llamada batch
                rows.addAll(fetchRowsBatch(apiService, "SUBTASK", subtaskIds));
            }
        }

        return rows;
    }

    /**
     * Consulta el endpoint batch {@code /api/activity-log/entities} y convierte
     * la respuesta en una lista de {@link ActivityRow}.
     * Devuelve lista vacía si {@code entityIds} está vacía o la llamada falla.
     *
     * @param apiService servicio HTTP del cliente
     * @param entityType tipo de entidad a consultar
     * @param entityIds  lista de identificadores de las entidades
     * @return lista de filas de actividad, o lista vacía si la llamada falla
     * @throws Exception si se produce un error al parsear la respuesta JSON
     */
    private List<ActivityRow> fetchRowsBatch(ApiService apiService,
                                             String entityType,
                                             List<Long> entityIds) throws Exception {
        if (entityIds.isEmpty()) return List.of();

        String idsParam = entityIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        HttpResponse<String> httpResponse = apiService.get(
                "/api/activity-log/entities?entityType=" + entityType + "&entityIds=" + idsParam);

        List<ActivityRow> rows = new ArrayList<>();
        if (httpResponse == null || httpResponse.statusCode() != 200) return rows;

        JsonNode response = objectMapper.readTree(httpResponse.body());
        if (response == null || !response.isArray()) return rows;

        for (JsonNode node : response) {
            String actionType  = node.path("actionType").asText();
            String entType     = node.path("entityType").asText();
            String rawDate     = node.path("createdAt").asText();
            String oldVal      = node.path("oldValue").asText("");
            String newVal      = node.path("newValue").asText("");
            String entityName  = node.path("entityName").asText("");

            String dateStr     = formatDate(rawDate);
            String actionLabel = getActionLabel(actionType);
            String entityId2   = node.path("entityId").asText("");
            String entityLabel = (!entityId2.isBlank() ? "#" + entityId2 + " " : "") + getEntityLabel(entType);
            String detail      = buildDetail(actionType, oldVal, newVal, entityName);

            rows.add(new ActivityRow(dateStr, actionLabel, entityLabel, detail, rawDate, actionType, entType));
        }
        return rows;
    }

    /**
     * Consulta la API para una única entidad y convierte la respuesta en una lista de {@link ActivityRow}.
     * Se mantiene para el caso {@code entityType} sin extra (una sola entidad: tarea, subtarea o proyecto).
     *
     * @param apiService servicio HTTP del cliente
     * @param entityType tipo de entidad a consultar
     * @param entityId   identificador de la entidad
     * @return lista de filas de actividad, o lista vacía si la llamada falla
     * @throws Exception si se produce un error al parsear la respuesta JSON
     */
    private List<ActivityRow> fetchRows(ApiService apiService, String entityType, Long entityId) throws Exception {
        HttpResponse<String> httpResponse = apiService.get(
                "/api/activity-log/entity?entityType=" + entityType + "&entityId=" + entityId);

        List<ActivityRow> rows = new ArrayList<>();
        if (httpResponse == null || httpResponse.statusCode() != 200) return rows;

        JsonNode response = objectMapper.readTree(httpResponse.body());
        if (response == null || !response.isArray()) return rows;

        for (JsonNode node : response) {
            String actionType  = node.path("actionType").asText();
            String entType     = node.path("entityType").asText();
            String rawDate     = node.path("createdAt").asText();
            String oldVal      = node.path("oldValue").asText("");
            String newVal      = node.path("newValue").asText("");
            String entityName  = node.path("entityName").asText("");

            String dateStr     = formatDate(rawDate);
            String actionLabel = getActionLabel(actionType);
            String entityId2   = node.path("entityId").asText("");
            String entityLabel = (!entityId2.isBlank() ? "#" + entityId2 + " " : "") + getEntityLabel(entType);
            String detail      = buildDetail(actionType, oldVal, newVal, entityName);

            rows.add(new ActivityRow(dateStr, actionLabel, entityLabel, detail, rawDate, actionType, entType));
        }
        return rows;
    }

    // -------------------------------------------------------------------------
    // Construcción del detalle
    // -------------------------------------------------------------------------

    /**
     * Construye el texto de detalle de un registro a partir de los valores
     * anterior y posterior al cambio.
     *
     * @param actionType tipo de acción realizada
     * @param oldVal     valor anterior al cambio
     * @param newVal     valor posterior al cambio
     * @param entityName nombre de la entidad, usado como fallback
     * @return cadena descriptiva del cambio realizado
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
            if (isStatus)   return MessageFormat.format(lm.get("actlog.detail.status"),   old, nw);
            if (isPriority) return MessageFormat.format(lm.get("actlog.detail.priority"), old, nw);
            if (isDate)     return MessageFormat.format(lm.get("actlog.detail.duedate"),  old, nw);
            return old + " → " + nw;
        }
        if (!newVal.isBlank()) return translateValue(newVal);
        if (!oldVal.isBlank()) return translateValue(oldVal);
        return entityName;
    }

    // -------------------------------------------------------------------------
    // Traducciones y mapeos
    // -------------------------------------------------------------------------

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
            case "DEPENDENCY_ADDED"         -> lm.get("common.dependency.added");
            case "DEPENDENCY_REMOVED"       -> lm.get("common.dependency.removed");
            default                         -> actionType;
        };
    }

    private String getEntityLabel(String entityType) {
        return switch (entityType) {
            case "TASK"    -> lm.get("common.task");
            case "SUBTASK" -> lm.get("actlog.entity.subtask");
            case "PROJECT" -> lm.get("common.project");
            case "PROFILE" -> lm.get("actlog.entity.profile");
            default        -> entityType;
        };
    }

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

    private boolean isStatusValue(String v) {
        return switch (v) { case "TODO","IN_PROGRESS","DONE","CANCELLED" -> true; default -> false; };
    }

    private boolean isPriorityValue(String v) {
        return switch (v) { case "LOW","MEDIUM","HIGH","URGENT" -> true; default -> false; };
    }

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

    private String getActionIcon(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED",  "SUBTASK_CREATED"                    -> "fas-plus-circle";
            case "TASK_EDITED",   "SUBTASK_EDITED",
                 "PROJECT_EDITED"                                       -> "fas-pen";
            case "TASK_DELETED",  "SUBTASK_DELETED",
                 "PROJECT_DELETED"                                      -> "fas-trash";
            case "TASK_PERMANENTLY_DELETED",
                 "PROJECT_PERMANENTLY_DELETED"                          -> "fas-times-circle";
            case "TASK_RESTORED", "PROJECT_RESTORED"                    -> "fas-undo";
            case "TASK_STATUS_CHANGED", "PROJECT_STATUS_CHANGED"        -> "fas-sync-alt";
            case "PROJECT_CREATED"                                      -> "fas-folder-plus";
            case "PROFILE_UPDATED"                                      -> "fas-user-edit";
            case "PASSWORD_CHANGED"                                     -> "fas-key";
            case "DEPENDENCY_ADDED", "DEPENDENCY_REMOVED"               -> "fas-link";
            default                                                     -> "fas-circle";
        };
    }

    private String getActionColor(String actionType) {
        return switch (actionType) {
            case "TASK_CREATED", "SUBTASK_CREATED", "PROJECT_CREATED"       -> "#22c55e";
            case "TASK_DELETED", "SUBTASK_DELETED", "PROJECT_DELETED"       -> "#f59e0b";
            case "TASK_PERMANENTLY_DELETED", "PROJECT_PERMANENTLY_DELETED"  -> "#e74c3c";
            case "TASK_RESTORED", "PROJECT_RESTORED"                        -> "#3b82f6";
            case "TASK_STATUS_CHANGED", "PROJECT_STATUS_CHANGED"            -> "#7c3aed";
            case "DEPENDENCY_ADDED", "DEPENDENCY_REMOVED"                   -> "#0ea5e9";
            default                                                         -> "#888888";
        };
    }

    private String getEntityIcon(String entityType) {
        return switch (entityType) {
            case "TASK"    -> "fas-tasks";
            case "SUBTASK" -> "fas-stream";
            case "PROJECT" -> "fas-folder";
            case "PROFILE" -> "fas-user";
            default        -> "fas-circle";
        };
    }

    // -------------------------------------------------------------------------
    // Tipos de datos
    // -------------------------------------------------------------------------

    /**
     * Registro que representa una fila de la tabla de historial de actividad.
     *
     * @param date       fecha formateada para mostrar
     * @param action     etiqueta localizada de la acción realizada
     * @param entity     tipo e identificador de la entidad afectada
     * @param detail     descripción del cambio realizado
     * @param rawDate    fecha en formato ISO, usada para ordenación
     * @param actionType código original de la acción, usado para mapear iconos
     * @param entityType código original del tipo de entidad, usado para mapear iconos
     */
    public record ActivityRow(
            String date, String action, String entity, String detail,
            String rawDate, String actionType, String entityType) {}
}