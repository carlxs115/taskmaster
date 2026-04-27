package com.taskmaster.taskmasterfrontend.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * DATEFORMATMANAGER
 *
 * Singleton que gestiona el formato de fecha preferido por el usuario.
 * La preferencia se persiste en java.util.prefs.Preferences.
 */
public class DateFormatManager {

    public enum DateFormat {
        DD_MM_YYYY,   // 15/04/2026
        DD_MM_YY,     // 15/04/26
        MM_DD_YYYY,   // 04/15/2026
        MM_DD_YY,     // 04/15/26
        YYYY_MM_DD,   // 2026-04-15
        DD_MMM_YYYY   // 15 abr 2026
    }

    private static final String PREF_KEY = "dateFormat";
    private static DateFormatManager instance;

    private DateFormat currentFormat;
    private final Preferences prefs = Preferences.userNodeForPackage(DateFormatManager.class);

    private DateFormatManager() {
        String saved = prefs.get(PREF_KEY, DateFormat.DD_MM_YYYY.name());
        try {
            currentFormat = DateFormat.valueOf(saved);
        } catch (IllegalArgumentException e) {
            currentFormat = DateFormat.DD_MM_YYYY;
        }
    }

    public static DateFormatManager getInstance() {
        if (instance == null) instance = new DateFormatManager();
        return instance;
    }

    public DateFormat getCurrentFormat() { return currentFormat; }

    public void setFormat(DateFormat format) {
        this.currentFormat = format;
        prefs.put(PREF_KEY, format.name());
    }

    public DateTimeFormatter getFormatter() {
        Locale locale = LanguageManager.getInstance().getCurrentLocale();
        return switch (currentFormat) {
            case DD_MM_YYYY  -> DateTimeFormatter.ofPattern("dd/MM/yyyy");
            case DD_MM_YY    -> DateTimeFormatter.ofPattern("dd/MM/yy");
            case MM_DD_YYYY  -> DateTimeFormatter.ofPattern("MM/dd/yyyy");
            case MM_DD_YY    -> DateTimeFormatter.ofPattern("MM/dd/yy");
            case YYYY_MM_DD  -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
            case DD_MMM_YYYY -> DateTimeFormatter.ofPattern("d MMM yyyy", locale);
        };
    }

    public String format(LocalDate date) {
        if (date == null) return "";
        return date.format(getFormatter());
    }

    /** Etiquetas visibles para el ComboBox, con ejemplo dinámico */
    public static String getLabel(DateFormat format) {
        LanguageManager lm = LanguageManager.getInstance();
        return switch (format) {
            case DD_MM_YYYY  -> lm.get("dateformat.dd_mm_yyyy");
            case DD_MM_YY    -> lm.get("dateformat.dd_mm_yy");
            case MM_DD_YYYY  -> lm.get("dateformat.mm_dd_yyyy");
            case MM_DD_YY    -> lm.get("dateformat.mm_dd_yy");
            case YYYY_MM_DD  -> lm.get("dateformat.yyyy_mm_dd");
            case DD_MMM_YYYY -> lm.get("dateformat.dd_mmm_yyyy");
        };
    }
}
