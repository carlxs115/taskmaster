package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Consumer;

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

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);

    @FXML private Label monthYearLabel;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private GridPane calendarGrid;
    @FXML private VBox root;

    private YearMonth currentMonth;
    private Runnable onClose;
    private Consumer<JsonNode> onOpenTask;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Nombres cortos de los días de la semana como claves de localización.
     * Usadas para construir la cabecera del grid (L M X J V S D).
     */
    private static final String[] DAY_KEYS = {
            "cal.mon", "cal.tue", "cal.wed", "cal.thu",
            "cal.fri", "cal.sat", "cal.sun"
    };

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Inicializa el controlador posicionando el calendario en el mes actual
     * y cargando las tareas correspondientes desde el backend.
     */
    @FXML
    public void initialize() {
        currentMonth = YearMonth.now();
        loadMonth();
    }

    /**
     * Registra el callback que se ejecutará al cerrar el calendario.
     *
     * @param onClose acción a ejecutar al cerrar
     */
    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /**
     * Registra el callback que se ejecutará al hacer clic en una tarea del calendario.
     *
     * @param cb consumidor que recibe el nodo JSON de la tarea seleccionada
     */
    public void setOnOpenTask(Consumer<JsonNode> cb) {
        this.onOpenTask = cb;
    }

    /**
     * Navega al mes anterior y recarga el calendario.
     */
    @FXML
    private void handlePrev() {
        currentMonth = currentMonth.minusMonths(1);
        loadMonth();
    }

    /**
     * Navega al mes siguiente y recarga el calendario.
     */
    @FXML
    private void handleNext() {
        currentMonth = currentMonth.plusMonths(1);
        loadMonth();
    }

    // -------------------------------------------------------------------------
    // Carga de datos
    // -------------------------------------------------------------------------

    /**
     * Actualiza la cabecera con el nombre del mes y el año, y solicita al
     * backend las tareas con fecha límite en el mes actualmente visible.
     * La llamada HTTP se realiza en un hilo secundario para no bloquear la UI.
     */
    private void loadMonth() {
        int year  = currentMonth.getYear();
        int month = currentMonth.getMonthValue();

        // Capitalizamos el nombre del mes para mostrarlo en la cabecera
        Locale locale = lm.getBundle().getLocale();
        String monthName = currentMonth.getMonth().getDisplayName(TextStyle.FULL, locale);
        monthYearLabel.setText(
                monthName.substring(0, 1).toUpperCase() + monthName.substring(1) + " " + year);

        Thread t = new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().get("/api/tasks/calendar?year=" + year + "&month=" + month);
                if (r.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(r.body());
                    Platform.runLater(() -> renderGrid(tasks));
                }
            } catch (Exception e) {
                log.error("Error al cargar las tareas del calendario: {}", e.getMessage());
                // Si falla la carga mostramos el grid vacío
                Platform.runLater(() -> renderGrid(objectMapper.createArrayNode()));
            }
        }, "calendar-loader");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Renderizado del grid
    // -------------------------------------------------------------------------

    /**
     * Construye el grid mensual a partir de los datos recibidos del backend.
     *
     * <p>La fila 0 contiene la cabecera con las iniciales de los días de la
     * semana localizadas. A partir de la fila 1 se distribuyen las celdas de
     * cada día, desplazadas según el día de la semana en que empieza el mes
     * (lunes = columna 0).</p>
     *
     * @param tasks array JSON con las tareas del mes
     */
    private void renderGrid(JsonNode tasks) {
        calendarGrid.getChildren().clear();
        calendarGrid.getRowConstraints().clear();

        // Fila 0: cabecera con los nombres cortos de los días localizados
        RowConstraints headerRow = new RowConstraints(32);
        calendarGrid.getRowConstraints().add(headerRow);

        for (int col = 0; col < DAY_KEYS.length; col++) {
            Label lbl = new Label(lm.get(DAY_KEYS[col]));
            lbl.getStyleClass().add("cal-day-header");
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            calendarGrid.add(lbl, col, 0);
        }

        // Agrupamos las tareas por día del mes para acceso rápido al construir celdas
        Map<Integer, List<JsonNode>> byDay = new HashMap<>();
        for (JsonNode t : tasks) {
            if (t.has("dueDate") && !t.get("dueDate").isNull()) {
                try {
                    int day = LocalDate.parse(t.get("dueDate").asText().substring(0, 10))
                            .getDayOfMonth();
                    byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(t);
                } catch (Exception ignored) {}
            }
        }

        // Calculamos el desplazamiento inicial según el día de la semana del día 1
        LocalDate first   = currentMonth.atDay(1);
        int startDow      = first.getDayOfWeek().getValue() - 1; // 0 = Lunes
        int daysInMonth   = currentMonth.lengthOfMonth();
        LocalDate today   = LocalDate.now();

        // Añadimos las restricciones de fila para las semanas (máximo 6 semanas)
        for (int r = 0; r < 6; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS);
            rc.setMinHeight(80);
            calendarGrid.getRowConstraints().add(rc);
        }

        int gridRow = 1;
        int gridCol = startDow;

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
     * @param day      número del día del mes (1-31)
     * @param today    fecha actual para detectar el día de hoy
     * @param dayTasks lista de tareas con fecha límite ese día
     * @return {@link VBox} con el contenido visual de la celda
     */
    private VBox buildDayCell(int day, LocalDate today, List<JsonNode> dayTasks) {
        VBox cell = new VBox(3);
        cell.getStyleClass().add("cal-cell");
        cell.setPadding(new Insets(6));

        boolean isToday = currentMonth.getYear()       == today.getYear()
                && currentMonth.getMonthValue() == today.getMonthValue()
                && day                          == today.getDayOfMonth();

        Label dayNum = new Label(String.valueOf(day));
        dayNum.getStyleClass().add(isToday ? "cal-day-num-today" : "cal-day-num");
        cell.getChildren().add(dayNum);

        // Mostramos máximo 3 chips y un indicador de cuántos más hay
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
     * <p>El chip muestra el identificador y el título truncado. Recibe la clase
     * CSS {@code cal-chip-{status}} para diferenciar el borde según el estado.
     * Al hacer clic invoca {@code onOpenTask} para navegar al detalle.</p>
     *
     * @param task nodo JSON con los datos de la tarea
     * @return {@link Label} configurado como chip interactivo
     */
    private Label buildTaskChip(JsonNode task) {
        String title  = task.get("title").asText();
        String status = task.has("status") ? task.get("status").asText() : "TODO";
        long taskId = task.get("id").asLong();

        // Truncamos el título para que quepa en la celda del calendario
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
     * al hacer clic fuera de él. Hereda los estilos CSS del scene principal
     * para que el tema se aplique correctamente.</p>
     *
     * @param anchor   nodo desde el que se ancla el popup
     * @param dayTasks lista completa de tareas del día
     */
    private void showOverflowPopup(Node anchor, List<JsonNode> dayTasks) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        VBox content = new VBox(6);
        content.getStyleClass().add("cal-overflow-popup");
        content.setPadding(new Insets(10, 12, 10, 12));

        // Cabecera con la fecha del día
        String dueDateStr = dayTasks.getFirst().get("dueDate").asText().substring(0, 10);
        LocalDate date    = LocalDate.parse(dueDateStr);
        Locale locale     = lm.getBundle().getLocale();
        String dateLabel  = date.getDayOfMonth() + " de "
                + date.getMonth().getDisplayName(TextStyle.FULL, locale);

        Label header = new Label(dateLabel);
        header.getStyleClass().add("cal-overflow-header");
        content.getChildren().add(header);
        content.getChildren().add(new Separator());

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

        // Heredamos el CSS del scene principal para mantener el tema activo
        Scene anchorScene = anchor.getScene();
        if (anchorScene != null) {
            content.getStylesheets().addAll(anchorScene.getStylesheets());
        }

        // Mostramos el popup justo debajo del nodo que lo disparó
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) {
            popup.show(anchor.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }
}