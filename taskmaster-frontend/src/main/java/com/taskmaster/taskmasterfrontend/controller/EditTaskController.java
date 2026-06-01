package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.SmartDatePicker;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador del diálogo de edición de tarea.
 *
 * <p>Recibe los datos actuales de la tarea, los muestra en el formulario
 * y permite modificar el título, la descripción, el estado, la prioridad
 * y la fecha límite. Los valores de los combos se muestran localizados
 * y se traducen al código del backend antes de enviar.</p>
 *
 * @author Carlos
 */
public class EditTaskController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private TextField estimatedDurationField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private SmartDatePicker dueDatePicker;
    @FXML private Label errorLabel;
    @FXML private Label dialogTitleLabel;

    private Runnable onCancel;
    private Long taskId;
    private Runnable onTaskUpdated;

    private final LanguageManager lm           = LanguageManager.getInstance();
    private final ObjectMapper    objectMapper  = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Registra el callback que se ejecutará tras actualizar la tarea correctamente.
     *
     * @param callback acción a ejecutar al completar la actualización
     */
    public void setOnTaskUpdated(Runnable callback) {
        this.onTaskUpdated = callback;
    }

    /**
     * Establece el título mostrado en la cabecera del diálogo.
     *
     * @param title texto a mostrar como título
     */
    public void setDialogTitle(String title) {
        dialogTitleLabel.setText(title);
    }

    /**
     * Inicializa los combos con sus valores localizados y selecciones por defecto.
     */
    @FXML
    public void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList(
                lm.get("status.todo"),
                lm.get("status.inprogress"),
                lm.get("status.done"),
                lm.get("status.submitted"),
                lm.get("status.cancelled")));
        statusCombo.setValue(lm.get("status.todo"));

        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low"),
                lm.get("priority.medium"),
                lm.get("priority.high"),
                lm.get("priority.urgent")));
        priorityCombo.setValue(lm.get("priority.medium"));

        titleField.setOnKeyPressed(e -> {if (e.getCode() == KeyCode.ENTER) handleSave();});
    }

    // -------------------------------------------------------------------------
    // Carga de datos
    // -------------------------------------------------------------------------

    /**
     * Inicializa el diálogo con el ID de la tarea y lanza una carga fresca desde
     * la API para garantizar que todos los campos (incluida la duración estimada)
     * reflejan el estado actual en base de datos.
     *
     * <p>Se pre-rellena el título desde los datos en memoria para que el formulario
     * no aparezca en blanco mientras llega la respuesta del backend.</p>
     *
     * @param task Nodo JSON con los datos de la tarea (puede ser caché de lista).
     */
    public void initData(JsonNode task) {
        this.taskId = task.get("id").asLong();

        // Pre-rellenamos el título inmediatamente para evitar pantalla en blanco
        titleField.setText(task.get("title").asText());

        // Carga fresca desde la API — garantiza que estimatedDuration y demás campos
        // están actualizados aunque el JSON en memoria venga de una versión cacheada
        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks/" + this.taskId);
                if (response.statusCode() == 200) {
                    JsonNode fresh = objectMapper.readTree(response.body());
                    Platform.runLater(() -> populateForm(fresh));
                }
            } catch (Exception e) {
                // Si la API falla, rellenamos con los datos en memoria como fallback
                Platform.runLater(() -> populateForm(task));
            }
        }, "edit-task-load");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Rellena todos los campos del formulario con los datos del nodo JSON recibido.
     *
     * @param task Nodo JSON con los datos actualizados de la tarea.
     */
    private void populateForm(JsonNode task) {
        titleField.setText(task.get("title").asText());

        if (task.has("description") && !task.get("description").isNull()) {
            descriptionField.setText(task.get("description").asText());
        }

        statusCombo.setValue(translateStatus(task.get("status").asText()));
        priorityCombo.setValue(translatePriority(task.get("priority").asText()));

        if (task.has("dueDate") && !task.get("dueDate").isNull()) {
            dueDatePicker.setValue(LocalDate.parse(task.get("dueDate").asText()));
        }

        if (task.has("estimatedDuration") && !task.get("estimatedDuration").isNull()) {
            estimatedDurationField.setText(
                    new BigDecimal(task.get("estimatedDuration").asText())
                            .stripTrailingZeros().toPlainString());
        }

        Platform.runLater(() -> {
            titleField.deselect();
            titleField.positionCaret(0);
            titleField.getParent().requestFocus();
        });
    }

    /**
     * Registra el callback que se ejecutará al pulsar cancelar.
     *
     * @param callback acción a ejecutar al cancelar
     */
    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------

    /**
     * Valida el formulario y envía los datos actualizados al backend.
     * Cierra el diálogo si la operación es exitosa.
     */
    @FXML
    private void handleSave() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError(lm.get("common.error.title.required"));
            return;
        }

        BigDecimal estimatedDuration = parseEstimatedDuration();
        if (estimatedDuration == null && !estimatedDurationField.getText().trim().isEmpty()) return;

        final BigDecimal finalEstimated = estimatedDuration;

        Thread t = new Thread(() -> {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("title",       title);
                body.put("description", descriptionField.getText().trim());
                body.put("status",      reverseStatus(statusCombo.getValue()));
                body.put("priority",    reversePriority(priorityCombo.getValue()));
                if (dueDatePicker.getValue() != null) {
                    body.put("dueDate", dueDatePicker.getValue().toString());
                }
                if (finalEstimated != null) {
                    body.put("estimatedDuration", finalEstimated);
                }

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().put("/api/tasks/" + taskId, body);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        if (onTaskUpdated != null) onTaskUpdated.run();
                        closeDialog();
                    } else if (response.statusCode() == 400) {
                        showError(lm.get("task.error.pending.subtasks"));
                    } else {
                        showError(lm.get("common.error.save"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }, "edit-task-save");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Ejecuta el callback de cancelación si está registrado,
     * o cierra el diálogo directamente en caso contrario.
     */
    @FXML
    private void handleCancel() {
        if (onCancel != null) onCancel.run();
        else closeDialog();
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Parsea y valida el campo de duración estimada.
     *
     * <p>Devuelve {@code null} si el campo está vacío (el campo es opcional).
     * Muestra un error y devuelve {@code null} si el valor no es numérico
     * o es inferior al mínimo permitido (0.1 horas).</p>
     *
     * @return duración estimada como {@link BigDecimal}, o {@code null} si el campo está vacío
     */
    private BigDecimal parseEstimatedDuration() {
        String text = estimatedDurationField.getText().trim();
        if (text.isEmpty()) return null;
        try {
            BigDecimal value = new BigDecimal(text.replace(",", "."));
            if (value.compareTo(new BigDecimal("0.1")) < 0) {
                showError(lm.get("estimated.duration.error.invalid"));
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            showError(lm.get("estimated.duration.error.invalid"));
            return null;
        }
    }

    /**
     * Cierra el diálogo actual.
     */
    private void closeDialog() {
        titleField.getScene().getWindow().hide();
    }

    /**
     * Traduce un código de estado del backend a su etiqueta localizada.
     *
     * @param s Código de estado (p.ej. {@code "IN_PROGRESS"}).
     * @return Etiqueta localizada correspondiente.
     */
    private String translateStatus(String s) {
        return switch (s) {
            case "TODO" -> lm.get("status.todo");
            case "IN_PROGRESS" -> lm.get("status.inprogress");
            case "DONE" -> lm.get("status.done");
            case "SUBMITTED" -> lm.get("status.submitted");
            case "CANCELLED" -> lm.get("status.cancelled");
            default -> s;
        };
    }

    /**
     * Traduce un código de prioridad del backend a su etiqueta localizada.
     *
     * @param p Código de prioridad (p.ej. {@code "HIGH"}).
     * @return Etiqueta localizada correspondiente.
     */
    private String translatePriority(String p) {
        return switch (p) {
            case "LOW" -> lm.get("priority.low");
            case "MEDIUM" -> lm.get("priority.medium");
            case "HIGH" -> lm.get("priority.high");
            case "URGENT" -> lm.get("priority.urgent");
            default -> p;
        };
    }

    /**
     * Traduce la etiqueta localizada de estado al código del backend.
     *
     * @param s Etiqueta localizada seleccionada en el combo.
     * @return Código de estado del backend.
     */
    private String reverseStatus(String s) {
        if (s.equals(lm.get("status.inprogress"))) return "IN_PROGRESS";
        else if (s.equals(lm.get("status.done"))) return "DONE";
        else if (s.equals(lm.get("status.submitted"))) return "SUBMITTED";
        else if (s.equals(lm.get("status.cancelled"))) return "CANCELLED";
        else return "TODO";
    }

    /**
     * Traduce la etiqueta localizada de prioridad al código del backend.
     *
     * @param p Etiqueta localizada seleccionada en el combo.
     * @return Código de prioridad del backend.
     */
    private String reversePriority(String p) {
        if (p.equals(lm.get("priority.low")))         return "LOW";
        else if (p.equals(lm.get("priority.high")))   return "HIGH";
        else if (p.equals(lm.get("priority.urgent"))) return "URGENT";
        else return "MEDIUM";
    }

    /**
     * Muestra un mensaje de error en la etiqueta de error del formulario.
     *
     * @param message Mensaje de error a mostrar.
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}