package com.taskmaster.taskmasterfrontend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
 * <p><b>Nota de concurrencia:</b> no es thread-safe por diseño,
 * ya que se usa exclusivamente desde el hilo de JavaFX.</p>
 *
 * @author Carlos
 */
public class TimeFormatManager {

    private static final Logger log = LoggerFactory.getLogger(TimeFormatManager.class);

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
            // Si el valor guardado es inválido usamos el por defecto
            currentFormat = TimeFormat.SYSTEM;
        }
    }

    /**
     * Devuelve la instancia única del singleton, creándola si aún no existe.
     *
     * @return instancia global de {@link TimeFormatManager}
     */
    public static TimeFormatManager getInstance() {
        if (instance == null) instance = new TimeFormatManager();
        return instance;
    }

    /**
     * Devuelve el formato de hora actualmente activo.
     *
     * @return formato activo
     */
    public TimeFormat getCurrentFormat() { return currentFormat; }

    /**
     * Establece un nuevo formato de hora y lo persiste en
     * {@link java.util.prefs.Preferences} para que se mantenga entre sesiones.
     *
     * @param format formato a aplicar y guardar
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
     * @return formateador de hora listo para usar
     */
    public DateTimeFormatter getFormatter() {
        return switch (currentFormat) {
            case H24    -> DateTimeFormatter.ofPattern("HH:mm");
            case H12    -> DateTimeFormatter.ofPattern("hh:mm a");
            // En modo SYSTEM delegamos la detección al sistema operativo
            case SYSTEM -> isSystemUsing12h()
                    ? DateTimeFormatter.ofPattern("hh:mm a")
                    : DateTimeFormatter.ofPattern("HH:mm");
        };
    }

    /**
     * Formatea una hora según el formato activo.
     *
     * @param time hora a formatear, o {@code null}
     * @return cadena con la hora formateada, o cadena vacía si la hora es {@code null}
     */
    public String format(LocalTime time) {
        if (time == null) return "";
        return time.format(getFormatter());
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Detecta si el sistema operativo usa la convención de 12 horas
     * inspeccionando el patrón del {@link SimpleDateFormat} por defecto.
     *
     * <p>La detección se basa en que el patrón de 12 horas contiene
     * {@code 'h'} minúscula y el de 24 horas contiene {@code 'H'} mayúscula.</p>
     *
     * @return {@code true} si el sistema usa convenio de 12 horas,
     *         {@code false} si usa 24 horas o no se puede detectar
     */
    private boolean isSystemUsing12h() {
        try {
            DateFormat df = DateFormat.getTimeInstance(
                    DateFormat.SHORT, Locale.getDefault());
            if (df instanceof SimpleDateFormat sdf) {
                String pattern = sdf.toPattern();
                // 'h' minúscula = 12 horas, 'H' mayúscula = 24 horas
                return pattern.contains("h") && !pattern.contains("H");
            }
        } catch (Exception e) {
            // Si no se puede detectar el formato del sistema usamos 24h como valor seguro
            log.warn("No se pudo detectar el formato de hora del sistema: {}", e.getMessage());
        }
        return false;
    }
}