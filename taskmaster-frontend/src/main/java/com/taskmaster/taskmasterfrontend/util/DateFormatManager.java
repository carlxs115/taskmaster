package com.taskmaster.taskmasterfrontend.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Singleton que gestiona el formato de fecha preferido por el usuario.
 *
 * <p>La preferencia se persiste entre sesiones mediante
 * {@link java.util.prefs.Preferences}. Proporciona tanto el
 * {@link DateTimeFormatter} correspondiente al formato activo como
 * la etiqueta localizada para mostrar en el combo de ajustes.</p>
 *
 * @author Carlos
 */
public class DateFormatManager {

    /**
     * Formatos de fecha disponibles para el usuario.
     * El comentario de cada valor indica un ejemplo con la fecha 11/03/2026.
     */
    public enum DateFormat {
        DD_MM_YYYY,   // 11/03/2026
        DD_MM_YY,     // 11/03/26
        MM_DD_YYYY,   // 03/11/2026
        MM_DD_YY,     // 03/11/26
        YYYY_MM_DD,   // 2026-03-11
        DD_MMM_YYYY   // 11 mar 2026
    }

    private static final String PREF_KEY = "dateFormat";
    private static DateFormatManager instance;

    private DateFormat currentFormat;
    private final Preferences prefs = Preferences.userNodeForPackage(DateFormatManager.class);

    /**
     * Constructor privado que carga la preferencia guardada o aplica
     * {@link DateFormat#DD_MM_YYYY} como valor por defecto.
     */
    private DateFormatManager() {
        String saved = prefs.get(PREF_KEY, DateFormat.DD_MM_YYYY.name());
        try {
            currentFormat = DateFormat.valueOf(saved);
        } catch (IllegalArgumentException e) {
            currentFormat = DateFormat.DD_MM_YYYY;
        }
    }

    /**
     * Devuelve la instancia única del singleton, creándola si aún no existe.
     *
     * @return Instancia global de {@link DateFormatManager}.
     */
    public static DateFormatManager getInstance() {
        if (instance == null) instance = new DateFormatManager();
        return instance;
    }

    /**
     * Devuelve el formato de fecha actualmente activo.
     *
     * @return Formato activo.
     */
    public DateFormat getCurrentFormat() { return currentFormat; }

    /**
     * Establece un nuevo formato de fecha y lo persiste en {@link java.util.prefs.Preferences}.
     *
     * @param format Formato a aplicar y guardar.
     */
    public void setFormat(DateFormat format) {
        this.currentFormat = format;
        prefs.put(PREF_KEY, format.name());
    }

    /**
     * Devuelve el {@link DateTimeFormatter} correspondiente al formato activo,
     * usando el locale del {@link LanguageManager} para formatos con nombre de mes.
     *
     * @return Formateador de fecha listo para usar.
     */
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

    /**
     * Formatea una fecha según el formato activo.
     *
     * @param date Fecha a formatear, o {@code null}.
     * @return Cadena con la fecha formateada, o cadena vacía si la fecha es {@code null}.
     */
    public String format(LocalDate date) {
        if (date == null) return "";
        return date.format(getFormatter());
    }

    /**
     * Devuelve la etiqueta localizada de un formato de fecha para mostrar
     * en el combo de ajustes.
     *
     * @param format Formato del que se quiere obtener la etiqueta.
     * @return Etiqueta localizada correspondiente al formato.
     */
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
