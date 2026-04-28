package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Singleton que gestiona el idioma de la interfaz de usuario.
 *
 * <p>Carga el {@link ResourceBundle} correspondiente al locale activo y expone
 * una propiedad observable para que los controladores puedan reaccionar a los
 * cambios de idioma en tiempo real. La preferencia se persiste localmente en
 * {@code ~/.taskmaster/config.properties}.</p>
 *
 * @author Carlos
 */
public class LanguageManager {

    private static final LanguageManager INSTANCE = new LanguageManager();
    private static final String BUNDLE_BASE = "com.taskmaster.taskmasterfrontend.i18n.messages";
    private static final String CONFIG_PATH =
            System.getProperty("user.home") + "/.taskmaster/config.properties";

    private final ObjectProperty<ResourceBundle> bundle = new SimpleObjectProperty<>();
    private Locale currentLocale = new Locale("es");

    /**
     * Constructor privado que carga la preferencia de idioma guardada
     * e inicializa el {@link ResourceBundle} correspondiente.
     */
    private LanguageManager() {
        currentLocale = loadLocalePreference();
        bundle.set(ResourceBundle.getBundle(BUNDLE_BASE, currentLocale));
    }

    /**
     * Persiste la preferencia de idioma en el fichero de configuración local.
     *
     * @param locale Locale a guardar.
     */
    public void saveLocalePreference(Locale locale) {
        try {
            java.io.File file = new java.io.File(CONFIG_PATH);
            file.getParentFile().mkdirs();
            java.util.Properties props = new java.util.Properties();
            props.setProperty("language", locale.getLanguage());
            try (var out = new java.io.FileOutputStream(file)) {
                props.store(out, "TaskMaster config");
            }
        } catch (Exception e) {
            System.err.println("No se pudo guardar la preferencia de idioma: " + e.getMessage());
        }
    }

    /**
     * Carga la preferencia de idioma desde el fichero de configuración local.
     * Devuelve español por defecto si el fichero no existe o no puede leerse.
     *
     * @return Locale guardado, o {@code Locale("es")} por defecto.
     */
    public Locale loadLocalePreference() {
        try {
            java.io.File file = new java.io.File(CONFIG_PATH);
            if (!file.exists()) return new Locale("es");
            java.util.Properties props = new java.util.Properties();
            try (var in = new java.io.FileInputStream(file)) {
                props.load(in);
            }
            String lang = props.getProperty("language", "es");
            return new Locale(lang);
        } catch (Exception e) {
            return new Locale("es");
        }
    }

    /**
     * Devuelve la instancia única del singleton.
     *
     * @return Instancia global de {@link LanguageManager}.
     */
    public static LanguageManager getInstance() {
        return INSTANCE;
    }

    /**
     * Devuelve el {@link ResourceBundle} del idioma activo.
     *
     * @return Bundle de recursos actual.
     */
    public ResourceBundle getBundle() {
        return bundle.get();
    }

    /**
     * Devuelve la propiedad observable del bundle, útil para registrar
     * listeners que reaccionen al cambio de idioma.
     *
     * @return Propiedad observable del {@link ResourceBundle}.
     */
    public ObjectProperty<ResourceBundle> bundleProperty() {
        return bundle;
    }

    /**
     * Devuelve la cadena localizada correspondiente a la clave indicada.
     * Si la clave no existe, devuelve {@code "?<clave>?"} para facilitar
     * la detección de traducciones ausentes.
     *
     * @param key Clave de localización.
     * @return Cadena localizada, o {@code "?<clave>?"} si no se encuentra.
     */
    public String get(String key) {
        try {
            return bundle.get().getString(key);
        } catch (Exception e) {
            return "?" + key + "?";
        }
    }

    /**
     * Devuelve el locale actualmente activo.
     *
     * @return Locale actual.
     */
    public Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Cambia el idioma activo actualizando el locale y recargando el bundle.
     * Los listeners del {@link #bundleProperty()} serán notificados automáticamente.
     *
     * @param locale Nuevo locale a aplicar.
     */
    public void setLocale(Locale locale) {
        currentLocale = locale;
        bundle.set(ResourceBundle.getBundle(BUNDLE_BASE, locale));
    }

    /**
     * Indica si el idioma activo es el español.
     *
     * @return {@code true} si el locale actual es {@code "es"}.
     */
    public boolean isSpanish() {
        return currentLocale.getLanguage().equals("es");
    }
}
