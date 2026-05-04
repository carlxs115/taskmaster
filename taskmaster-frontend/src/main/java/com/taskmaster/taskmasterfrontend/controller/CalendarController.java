package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import javax.swing.*;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controlador de la vista de calendario.
 *
 * <p>Muestra un grid mensual con los días del mes y, sobre cada día,
 * chips de las tareas con fecha límite en esa fecha. Al hacer clic
 * en un chip se navega al detalle de la tarea.</p>
 *
 * @author Carlos
 */
public class CalendarController {

    @FXML private Label monthYearLabel;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private GridPane calendarGrid;
    @FXML private VBox root;

    private YearMonth currentMonth;
    private Runnable onClose;
    private java.util.function.Consumer<JsonNode> onOpenTask;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    /** Nombres cortos de los días de la semana (L M X J V S D). */
    private static final String[] DAY_KEYS = {
            "cal.mon", "cal.tue", "cal.wed", "cal.thu",
            "cal.fri", "cal.sat", "cal.sun"
    };

    /**
     * Inicializa el controlador posicionando el calendario en el mes actual
     * y cargando las tareas correspondientes desde el backend.
     */
    @FXML
    public void initialize() {
        currentMonth = YearMonth.now();
        loadMonth();
    }

    public void setOnClose(Runnable onClose)                              { this.onClose = onClose; }
    public void setOnOpenTask(java.util.function.Consumer<JsonNode> cb)   { this.onOpenTask = cb; }

    @FXML private void handlePrev() { currentMonth = currentMonth.minusMonths(1); loadMonth(); }
    @FXML private void handleNext() { currentMonth = currentMonth.plusMonths(1);  loadMonth(); }

    // ── Carga ─────────────────────────────────────────────────────────────────

    /**
     * Actualiza la cabecera con el nombre del mes y el año, y solicita al
     * backend las tareas con fecha límite en el mes actualmente visible.
     * La llamada HTTP se realiza en un hilo secundario; el grid se renderiza
     * en el hilo de JavaFX al recibir la respuesta.
     */
    private void loadMonth() {
        int year  = currentMonth.getYear();
        int month = currentMonth.getMonthValue();

        Locale locale = lm.getBundle().getLocale();
        String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, locale);
        monthYearLabel.setText(
                monthName.substring(0, 1).toUpperCase() + monthName.substring(1) + " " + year
        );

        new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().get("/api/tasks/calendar?year=" + year + "&month=" + month);
                if (r.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(r.body());
                    Platform.runLater(() -> renderGrid(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> renderGrid(objectMapper.createArrayNode()));
            }
        }).start();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    /**
     * Construye el grid mensual a partir de los datos recibidos del backend.
     *
     * <p>La fila 0 contiene la cabecera con las iniciales de los días de la
     * semana. A partir de la fila 1 se distribuyen las celdas de cada día,
     * desplazadas según el día de la semana en que empieza el mes (lunes=0).</p>
     *
     * @param tasks Array JSON con las tareas del mes, tal como las devuelve
     *              {@code GET /api/tasks/calendar}.
     */
    private void renderGrid(JsonNode tasks) {
        calendarGrid.getChildren().clear();
        calendarGrid.getRowConstraints().clear();

        // Fila 0: cabecera días de la semana
        RowConstraints headerRow = new RowConstraints(32);
        calendarGrid.getRowConstraints().add(headerRow);

        String[] dayLabels = {"L", "M", "X", "J", "V", "S", "D"};
        for (int col = 0; col < 7; col++) {
            Label lbl = new Label(dayLabels[col]);
            lbl.getStyleClass().add("cal-day-header");
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            calendarGrid.add(lbl, col, 0);
        }

        // Agrupar tareas por día
        java.util.Map<Integer, List<JsonNode>> byDay = new java.util.HashMap<>();
        for (JsonNode t : tasks) {
            if (t.has("dueDate") && !t.get("dueDate").isNull()) {
                try {
                    int day = LocalDate.parse(t.get("dueDate").asText().substring(0, 10))
                            .getDayOfMonth();
                    byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(t);
                } catch (Exception ignored) {}
            }
        }

        // Rellenar celdas
        LocalDate first     = currentMonth.atDay(1);
        int startDow        = first.getDayOfWeek().getValue() - 1; // 0=Lun
        int daysInMonth     = currentMonth.lengthOfMonth();
        LocalDate today     = LocalDate.now();

        int gridRow = 1;
        int gridCol = startDow;

        // Filas dinámicas (máximo 6)
        for (int r = 0; r < 6; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS);
            rc.setMinHeight(80);
            calendarGrid.getRowConstraints().add(rc);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            VBox cell = buildDayCell(day, today, byDay.getOrDefault(day, List.of()));
            calendarGrid.add(cell, gridCol, gridRow);

            gridCol++;
            if (gridCol == 7) { gridCol = 0; gridRow++; }
        }
    }

    /**
     * Construye la celda visual de un día del mes con su número y los chips
     * de las tareas con fecha límite ese día.
     *
     * <p>Muestra un máximo de 3 chips; si hay más tareas, añade una etiqueta
     * {@code +N más}. El día actual recibe el estilo {@code cal-day-num-today}.</p>
     *
     * @param day      Número del día del mes (1-31).
     * @param today    Fecha actual, usada para detectar el día de hoy.
     * @param dayTasks Lista de tareas con fecha límite en ese día.
     * @return {@link VBox} con el contenido visual de la celda.
     */
    private VBox buildDayCell(int day, LocalDate today, List<JsonNode> dayTasks) {
        VBox cell = new VBox(3);
        cell.getStyleClass().add("cal-cell");
        cell.setPadding(new Insets(6, 6, 6, 6));

        boolean isToday = (currentMonth.getYear()       == today.getYear()
                && currentMonth.getMonthValue() == today.getMonthValue()
                && day                          == today.getDayOfMonth());

        Label dayNum = new Label(String.valueOf(day));
        dayNum.getStyleClass().add(isToday ? "cal-day-num-today" : "cal-day-num");
        cell.getChildren().add(dayNum);

        // Chips de tareas (máximo 3 visibles + "+N más")
        int max = 3;
        for (int i = 0; i < Math.min(dayTasks.size(), max); i++) {
            cell.getChildren().add(buildTaskChip(dayTasks.get(i)));
        }

        if (dayTasks.size() > max) {
            int remaining = dayTasks.size() - max;
            Label more = new Label("+" + remaining + " " + lm.get("cal.more"));
            more.getStyleClass().add("cal-chip-more");
            more.setStyle("-fx-cursor: hand;");
            more.setOnMouseClicked(e -> {
                showOverflowPopup(more, dayTasks);
                e.consume();
            });
            cell.getChildren().add(more);
        }

        return cell;
    }

    /**
     * Construye el chip visual de una tarea para su representación en el grid.
     *
     * <p>El chip muestra el identificador y el título truncado de la tarea.
     * Recibe la clase CSS {@code cal-chip-{status}} para diferenciar el borde
     * según el estado sin hardcodear colores. Al hacer clic invoca
     * {@code onOpenTask} para navegar al detalle de la tarea.</p>
     *
     * @param task Nodo JSON con los datos de la tarea.
     * @return {@link Label} configurado como chip interactivo.
     */
    private Label buildTaskChip(JsonNode task) {
        String title  = task.get("title").asText();
        String status = task.has("status") ? task.get("status").asText() : "TODO";
        Long   taskId = task.get("id").asLong();

        String display = title.length() > 16 ? title.substring(0, 14) + "…" : title;

        Label chip = new Label("#" + taskId + "  " + display);
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.getStyleClass().addAll("cal-chip", "cal-chip-" + status.toLowerCase().replace("_", "-"));
        chip.setOnMouseClicked(e -> {
            if (onOpenTask != null) onOpenTask.accept(task);
        });
        return chip;
    }

    /**
     * Muestra un popup flotante con todos los chips de tareas del día indicado.
     *
     * <p>Se ancla sobre el nodo que lo dispara y se cierra automáticamente
     * al hacer clic fuera de él. Cada chip navega al detalle de su tarea
     * igual que los chips del grid.</p>
     *
     * @param anchor   Nodo desde el que se ancla el popup (etiqueta "+N más").
     * @param dayTasks Lista completa de tareas del día.
     */
    private void showOverflowPopup(Node anchor, List<JsonNode> dayTasks) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        VBox content = new VBox(6);
        content.getStyleClass().add("cal-overflow-popup");
        content.setPadding(new Insets(10, 12, 10, 12));

        // Cabecera con la fecha
        String dueDateStr = dayTasks.get(0).get("dueDate").asText().substring(0, 10);
        LocalDate date = LocalDate.parse(dueDateStr);
        Locale locale = lm.getBundle().getLocale();
        String dateLabel = date.getDayOfMonth() + " de "
                + date.getMonth().getDisplayName(TextStyle.FULL, locale);
        Label header = new Label(dateLabel);
        header.getStyleClass().add("cal-overflow-header");

        content.getChildren().add(header);
        content.getChildren().add(new javafx.scene.control.Separator());

        for (JsonNode task : dayTasks) {
            Label chip = buildTaskChip(task);
            chip.setMaxWidth(Double.MAX_VALUE);
            chip.setOnMouseClicked(e -> {
                popup.hide();
                if (onOpenTask != null) onOpenTask.accept(task);
            });
            content.getChildren().add(chip);
        }

        popup.getContent().add(content);

        // Aplicar el tema activo al popup
        Scene anchorScene = anchor.getScene();
        if (anchorScene != null) {
            content.getStylesheets().addAll(anchorScene.getStylesheets());
        }

        javafx.geometry.Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) {
            popup.show(anchor.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }
}
