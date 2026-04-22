package com.taskmaster.taskmasterfrontend.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;


/**
 * TIMEFORMATMANAGER
 *
 * Singleton que gestiona el formato de horas preferido por el usuario.
 * Las opciones son: SYSTEM (formato del sistema), H24 (24h), H12 (12h AM/PM).
 * La preferencia se persiste en java.util.prefs.Preferences.
 */
public class TimeFormatManager {

    public enum TimeFormat { SYSTEM, H24, H12 }

    private static final String PREF_KEY = "timeFormat";
    private static TimeFormatManager instance;

    private TimeFormat currentFormat;
    private final Preferences prefs = Preferences.userNodeForPackage(TimeFormatManager.class);

    private TimeFormatManager() {
        String saved = prefs.get(PREF_KEY, TimeFormat.SYSTEM.name());
        try {
            currentFormat = TimeFormat.valueOf(saved);
        } catch (IllegalArgumentException e) {
            currentFormat = TimeFormat.SYSTEM;
        }
    }

    public static TimeFormatManager getInstance() {
        if (instance == null) instance = new TimeFormatManager();
        return instance;
    }

    public TimeFormat getCurrentFormat() { return currentFormat; }

    public void setFormat(TimeFormat format) {
        this.currentFormat = format;
        prefs.put(PREF_KEY, format.name());
    }

    /**
     * Devuelve un DateTimeFormatter según la preferencia activa.
     * SYSTEM detecta si el locale del sistema usa 12h o 24h.
     */
    public DateTimeFormatter getFormatter() {
        return switch (currentFormat) {
            case H24    -> DateTimeFormatter.ofPattern("HH:mm");
            case H12    -> DateTimeFormatter.ofPattern("hh:mm a");
            case SYSTEM -> isSystemUsing12h()
                    ? DateTimeFormatter.ofPattern("hh:mm a")
                    : DateTimeFormatter.ofPattern("HH:mm");
        };
    }

    /**
     * Formatea un LocalTime directamente con la preferencia activa.
     */
    public String format(LocalTime time) {
        if (time == null) return "";
        return time.format(getFormatter());
    }

    /** Detecta si el sistema operativo usa convención 12h */
    private boolean isSystemUsing12h() {
        try {
            java.text.DateFormat df = java.text.DateFormat.getTimeInstance(
                    java.text.DateFormat.SHORT, java.util.Locale.getDefault());
            if (df instanceof java.text.SimpleDateFormat sdf) {
                String pattern = sdf.toPattern();
                // 'h' minúscula = 12h, 'H' mayúscula = 24h
                return pattern.contains("h") && !pattern.contains("H");
            }
        } catch (Exception e) {
            // fallback seguro
        }
        return false; // si no se puede detectar, 24h por defecto
    }
}
