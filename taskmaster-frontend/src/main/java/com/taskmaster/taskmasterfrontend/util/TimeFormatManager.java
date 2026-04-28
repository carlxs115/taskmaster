package com.taskmaster.taskmasterfrontend.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;


/**
 * Singleton que gestiona el formato de hora preferido por el usuario.
 *
 * <p>Ofrece tres modos: {@link TimeFormat#SYSTEM} detecta automáticamente
 * si el sistema operativo usa convención de 12 o 24 horas; {@link TimeFormat#H24}
 * fuerza el formato de 24 horas; {@link TimeFormat#H12} fuerza el formato
 * de 12 horas con indicador AM/PM. La preferencia se persiste entre sesiones
 * mediante {@link java.util.prefs.Preferences}.</p>
 *
 * @author Carlos
 */
public class TimeFormatManager {

    /** Formatos de hora disponibles para el usuario. */
    public enum TimeFormat {
        /** Detecta automáticamente el convenio del sistema operativo. */
        SYSTEM,
        /** Formato de 24 horas (p.ej. {@code 14:30}). */
        H24,
        /** Formato de 12 horas con AM/PM (p.ej. {@code 02:30 PM}). */
        H12
    }

    private static final String PREF_KEY = "timeFormat";
    private static TimeFormatManager instance;

    private TimeFormat currentFormat;
    private final Preferences prefs = Preferences.userNodeForPackage(TimeFormatManager.class);

    /**
     * Constructor privado que carga la preferencia guardada o aplica
     * {@link TimeFormat#SYSTEM} como valor por defecto.
     */
    private TimeFormatManager() {
        String saved = prefs.get(PREF_KEY, TimeFormat.SYSTEM.name());
        try {
            currentFormat = TimeFormat.valueOf(saved);
        } catch (IllegalArgumentException e) {
            currentFormat = TimeFormat.SYSTEM;
        }
    }

    /**
     * Devuelve la instancia única del singleton, creándola si aún no existe.
     *
     * @return Instancia global de {@link TimeFormatManager}.
     */
    public static TimeFormatManager getInstance() {
        if (instance == null) instance = new TimeFormatManager();
        return instance;
    }

    /**
     * Devuelve el formato de hora actualmente activo.
     *
     * @return Formato activo.
     */
    public TimeFormat getCurrentFormat() { return currentFormat; }

    /**
     * Establece un nuevo formato de hora y lo persiste en {@link java.util.prefs.Preferences}.
     *
     * @param format Formato a aplicar y guardar.
     */
    public void setFormat(TimeFormat format) {
        this.currentFormat = format;
        prefs.put(PREF_KEY, format.name());
    }

    /**
     * Devuelve el {@link DateTimeFormatter} correspondiente al formato activo.
     * En modo {@link TimeFormat#SYSTEM}, detecta si el sistema usa convenio
     * de 12 o 24 horas y aplica el patrón correspondiente.
     *
     * @return Formateador de hora listo para usar.
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
     * Formatea una hora según el formato activo.
     *
     * @param time Hora a formatear, o {@code null}.
     * @return Cadena con la hora formateada, o cadena vacía si la hora es {@code null}.
     */
    public String format(LocalTime time) {
        if (time == null) return "";
        return time.format(getFormatter());
    }

    /**
     * Detecta si el sistema operativo usa la convención de 12 horas
     * inspeccionando el patrón del {@link java.text.SimpleDateFormat} por defecto.
     * En caso de error devuelve {@code false} (24 horas como valor seguro).
     *
     * @return {@code true} si el sistema usa convenio de 12 horas.
     */
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
