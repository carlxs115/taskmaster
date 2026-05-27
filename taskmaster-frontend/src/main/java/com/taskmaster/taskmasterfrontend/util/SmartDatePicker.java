package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Selector de fecha ergonómico: TextField con máscara DD/MM/AAAA más un
 * botón que abre un popup de calendario con combos directos de mes y año.
 * Sustituye al DatePicker nativo exponiendo la misma API básica:
 * getValue(), setValue() y valueProperty().
 */
public class SmartDatePicker extends HBox {

    // ─── Estado ────────────────────────────────────────────────────────────────

    private final ObjectProperty<LocalDate> value = new SimpleObjectProperty<>();

    private final TextField  textField = new TextField();
    private final Popup      popup     = new Popup();
    private GridPane         calGrid;
    private ComboBox<String> monthCombo;
    private ComboBox<Integer> yearCombo;
    private VBox             popupRoot;

    private YearMonth displayedMonth  = YearMonth.now();
    private LocalDate minDate         = null;
    private LocalDate maxDate         = null;

    // Evita ciclos al actualizar el textField desde el código
    private boolean updatingText = false;

    // ─── Constructor ───────────────────────────────────────────────────────────

    public SmartDatePicker() {
        super(0);
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        buildTextField();
        buildPopup();
        buildLayout();
    }

    // ─── API pública (compatible con DatePicker) ────────────────────────────────

    public ObjectProperty<LocalDate> valueProperty() { return value; }
    public LocalDate getValue()                       { return value.get(); }

    public void setValue(LocalDate date) {
        updatingText = true;
        value.set(date);
        if (date != null) {
            textField.setText(formatDate(date));
            applySuccessStyle();
            displayedMonth = YearMonth.from(date);
        } else {
            textField.setText("");
            clearValidationStyle();
        }
        updatingText = false;
    }

    public void setMinDate(LocalDate min) { this.minDate = min; }
    public void setMaxDate(LocalDate max) { this.maxDate = max; }

    // ─── TextField con máscara DD/MM/AAAA ──────────────────────────────────────

    private void buildTextField() {
        textField.setPromptText("DD/MM/AAAA");
        textField.getStyleClass().add("smart-date-field");
        textField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textField, Priority.ALWAYS);
        textField.setTextFormatter(new TextFormatter<>(buildMaskFilter()));

        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingText) return;
            String digits = newVal.replaceAll("[^0-9]", "");
            if (digits.length() == 8) {
                parseAndCommit(newVal);
            } else if (newVal.isEmpty()) {
                value.set(null);
                clearValidationStyle();
            } else {
                value.set(null);
                clearValidationStyle();
            }
        });

        // Validar al perder el foco
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) validateAndStyle();
        });
    }

    private UnaryOperator<TextFormatter.Change> buildMaskFilter() {
        return change -> {
            // Construir dígitos del nuevo texto propuesto
            String raw = change.getControlNewText().replaceAll("[^0-9]", "");
            if (raw.length() > 8) raw = raw.substring(0, 8);

            String formatted = applyMask(raw);

            // Reemplazar todo el contenido con el texto reformateado
            change.setRange(0, change.getControlText().length());
            change.setText(formatted);
            change.setCaretPosition(formatted.length());
            change.setAnchor(formatted.length());
            return change;
        };
    }

    /** Formatea hasta 8 dígitos como DD/MM/AAAA. */
    private String applyMask(String digits) {
        if (digits.length() <= 2) return digits;
        if (digits.length() <= 4) return digits.substring(0, 2) + "/" + digits.substring(2);
        return digits.substring(0, 2) + "/" + digits.substring(2, 4) + "/" + digits.substring(4);
    }

    private String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%04d",
                date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private void parseAndCommit(String text) {
        try {
            String[] parts = text.split("/");
            if (parts.length != 3) { rejectDate(); return; }
            int day   = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year  = Integer.parseInt(parts[2]);
            LocalDate date = LocalDate.of(year, month, day);
            if (minDate != null && date.isBefore(minDate)) { rejectDate(); return; }
            if (maxDate != null && date.isAfter(maxDate))  { rejectDate(); return; }
            value.set(date);
            displayedMonth = YearMonth.from(date);
            applySuccessStyle();
        } catch (Exception e) {
            rejectDate();
        }
    }

    private void validateAndStyle() {
        String text = textField.getText();
        if (text == null || text.isEmpty()) { clearValidationStyle(); return; }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() == 8) parseAndCommit(text);
        else applyErrorStyle();
    }

    private void rejectDate() {
        value.set(null);
        applyErrorStyle();
    }

    private void applyErrorStyle() {
        textField.getStyleClass().removeAll("smart-date-valid");
        if (!textField.getStyleClass().contains("smart-date-error"))
            textField.getStyleClass().add("smart-date-error");
    }

    private void applySuccessStyle() {
        textField.getStyleClass().removeAll("smart-date-error");
        if (!textField.getStyleClass().contains("smart-date-valid"))
            textField.getStyleClass().add("smart-date-valid");
    }

    private void clearValidationStyle() {
        textField.getStyleClass().removeAll("smart-date-error", "smart-date-valid");
    }

    // ─── Popup de calendario ───────────────────────────────────────────────────

    private void buildPopup() {
        popup.setAutoHide(true);
        popup.setConsumeAutoHidingEvents(false);

        popupRoot = new VBox(0);
        popupRoot.getStyleClass().add("smart-date-popup");
        popupRoot.setStyle(
            "-fx-border-color: -tm-border;" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 14, 0, 0, 4);"
        );

        popupRoot.getChildren().addAll(buildPopupHeader(), buildCalendarBody());
        popup.getContent().add(popupRoot);
    }

    private HBox buildPopupHeader() {
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color: -tm-accent;" +
            "-fx-background-radius: 8 8 0 0;" +
            "-fx-padding: 10 12;"
        );

        Button prevBtn = navButton("‹");
        prevBtn.setOnAction(e -> navigateMonth(-1));

        monthCombo = new ComboBox<>();
        styleHeaderCombo(monthCombo);
        Locale spanishLocale = new Locale("es", "ES");
        for (int m = 1; m <= 12; m++) {
            String name = Month.of(m).getDisplayName(TextStyle.FULL, spanishLocale);
            monthCombo.getItems().add(capitalize(name));
        }
        monthCombo.setOnAction(e -> {
            if (monthCombo.getValue() != null && !monthCombo.isFocused()) return;
            int idx = monthCombo.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                displayedMonth = YearMonth.of(displayedMonth.getYear(), idx + 1);
                refreshCalendar();
            }
        });

        yearCombo = new ComboBox<>();
        styleHeaderCombo(yearCombo);
        int now = LocalDate.now().getYear();
        for (int y = now - 120; y <= now + 20; y++) yearCombo.getItems().add(y);
        yearCombo.setOnAction(e -> {
            if (yearCombo.getValue() != null) {
                displayedMonth = YearMonth.of(yearCombo.getValue(), displayedMonth.getMonth());
                refreshCalendar();
            }
        });

        Button nextBtn = navButton("›");
        nextBtn.setOnAction(e -> navigateMonth(1));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(prevBtn, monthCombo, yearCombo, spacer, nextBtn);
        return header;
    }

    private VBox buildCalendarBody() {
        VBox body = new VBox(4);
        body.setStyle("-fx-background-color: -tm-bg-surface; -fx-background-radius: 0 0 8 8; -fx-padding: 10;");

        String[] dayNames = {"L", "M", "X", "J", "V", "S", "D"};
        GridPane daysHeader = new GridPane();
        daysHeader.setHgap(2);
        for (int i = 0; i < 7; i++) {
            Label lbl = new Label(dayNames[i]);
            lbl.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: -tm-text-muted;" +
                "-fx-font-weight: bold; -fx-min-width: 32px; -fx-alignment: CENTER;"
            );
            daysHeader.add(lbl, i, 0);
        }

        calGrid = new GridPane();
        calGrid.setHgap(2);
        calGrid.setVgap(2);

        body.getChildren().addAll(daysHeader, calGrid);
        return body;
    }

    private void refreshCalendar() {
        calGrid.getChildren().clear();
        syncHeaderCombos();

        LocalDate today    = LocalDate.now();
        LocalDate selected = value.get();
        LocalDate firstDay = displayedMonth.atDay(1);
        int startCol       = firstDay.getDayOfWeek().getValue() - 1; // Mon=0

        int col = startCol;
        int row = 0;

        // Relleno del mes anterior
        YearMonth prevMonth = displayedMonth.minusMonths(1);
        int prevLen = prevMonth.lengthOfMonth();
        for (int i = 0; i < startCol; i++) {
            calGrid.add(otherMonthLabel(prevLen - startCol + 1 + i), i, 0);
        }

        // Días del mes actual
        for (int day = 1; day <= displayedMonth.lengthOfMonth(); day++) {
            LocalDate date = displayedMonth.atDay(day);
            Button btn = dayButton(day, date.equals(today), date.equals(selected));
            btn.setOnAction(e -> selectDate(date));
            calGrid.add(btn, col, row);
            col++;
            if (col == 7) { col = 0; row++; }
        }

        // Relleno del mes siguiente
        int nextDay = 1;
        while (col > 0 && col < 7) {
            calGrid.add(otherMonthLabel(nextDay++), col++, row);
        }
    }

    private void syncHeaderCombos() {
        // Actualizar combos sin disparar sus listeners
        monthCombo.getSelectionModel().select(displayedMonth.getMonthValue() - 1);
        int yearIdx = yearCombo.getItems().indexOf(displayedMonth.getYear());
        if (yearIdx >= 0) yearCombo.getSelectionModel().select(yearIdx);
    }

    private void navigateMonth(int delta) {
        displayedMonth = displayedMonth.plusMonths(delta);
        refreshCalendar();
    }

    private void selectDate(LocalDate date) {
        updatingText = true;
        value.set(date);
        textField.setText(formatDate(date));
        displayedMonth = YearMonth.from(date);
        applySuccessStyle();
        popup.hide();
        updatingText = false;
    }

    private Button dayButton(int day, boolean isToday, boolean isSelected) {
        Button btn = new Button(String.valueOf(day));
        btn.setMinWidth(32);
        btn.setMinHeight(28);

        String baseRadius = "-fx-background-radius: 6; -fx-font-size: 12px; -fx-cursor: hand; -fx-min-width: 32px; -fx-min-height: 28px;";
        if (isSelected) {
            btn.setStyle(baseRadius + "-fx-background-color: -tm-accent; -fx-text-fill: -tm-text-on-accent; -fx-font-weight: bold;");
        } else if (isToday) {
            btn.setStyle(baseRadius + "-fx-background-color: -tm-accent-subtle; -fx-text-fill: -tm-accent; -fx-font-weight: bold;");
        } else {
            btn.setStyle(baseRadius + "-fx-background-color: transparent; -fx-text-fill: -tm-text-primary;");
            btn.setOnMouseEntered(e -> btn.setStyle(baseRadius + "-fx-background-color: -tm-bg-hover; -fx-text-fill: -tm-accent;"));
            btn.setOnMouseExited(e ->  btn.setStyle(baseRadius + "-fx-background-color: transparent; -fx-text-fill: -tm-text-primary;"));
        }
        return btn;
    }

    private Label otherMonthLabel(int day) {
        Label lbl = new Label(String.valueOf(day));
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: -tm-border; -fx-min-width: 32px; -fx-min-height: 28px; -fx-alignment: CENTER;");
        return lbl;
    }

    // ─── Layout ─────────────────────────────────────────────────────────────────

    private void buildLayout() {
        Button calBtn = new Button("📅");
        calBtn.getStyleClass().add("smart-date-cal-btn");
        calBtn.setStyle(
            "-fx-background-color: -tm-bg-input;" +
            "-fx-border-color: -tm-border;" +
            "-fx-border-width: 1.5 1.5 1.5 0;" +
            "-fx-border-radius: 0 6 6 0;" +
            "-fx-background-radius: 0 6 6 0;" +
            "-fx-pref-height: 38px;" +
            "-fx-padding: 0 10;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 14px;"
        );
        calBtn.setOnMouseEntered(e -> calBtn.setStyle(calBtn.getStyle()
            .replace("-tm-bg-input", "-tm-bg-hover")));
        calBtn.setOnMouseExited(e -> calBtn.setStyle(calBtn.getStyle()
            .replace("-tm-bg-hover", "-tm-bg-input")));
        calBtn.setOnAction(e -> togglePopup());

        getChildren().addAll(textField, calBtn);
    }

    private void togglePopup() {
        if (popup.isShowing()) { popup.hide(); return; }

        // Aplicar el tema actual al popup
        if (getScene() != null) {
            popupRoot.getStylesheets().setAll(getScene().getStylesheets());
        }

        refreshCalendar();

        javafx.geometry.Bounds bounds = textField.localToScreen(textField.getBoundsInLocal());
        if (bounds == null) return;
        popup.show(textField, bounds.getMinX(), bounds.getMaxY() + 4);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Button navButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.2);" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 16px;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 2 8;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle()
            .replace("rgba(255,255,255,0.2)", "rgba(255,255,255,0.35)")));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle()
            .replace("rgba(255,255,255,0.35)", "rgba(255,255,255,0.2)")));
        return btn;
    }

    private void styleHeaderCombo(ComboBox<?> combo) {
        combo.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 12px;" +
            "-fx-background-radius: 6;" +
            "-fx-border-color: rgba(255,255,255,0.3);" +
            "-fx-border-radius: 6;" +
            "-fx-padding: 3 6;"
        );
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
