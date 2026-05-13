package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Singleton que gestiona el idioma de la interfaz de usuario.
 *
 * <p>Carga el {@link ResourceBundle} correspondiente al locale activo y expone
 * una propiedad observable para que los controladores puedan reaccionar a los
 * cambios de idioma en tiempo real. La preferencia se persiste localmente en
 * {@code ~/.taskmaster/config.properties}.</p>
 *
 * <p><b>Nota de concurrencia:</b> el singleton se inicializa de forma eager
 * ({@code static final}), lo que garantiza thread-safety gracias al mecanismo
 * de inicialización de clases de la JVM.</p>
 *
 * @author Carlos
 */
public class LanguageManager {

    private static final Logger log = LoggerFactory.getLogger(LanguageManager.class);

    // Singleton eager - thread-safe por garantía de la JVM
    private static final LanguageManager INSTANCE = new LanguageManager();

    private static final String BUNDLE_BASE = "com.taskmaster.taskmasterfrontend.i18n.messages";
    private static final String CONFIG_PATH = System.getProperty("user.home") + "/.taskmaster/config.properties";

    private final ObjectProperty<ResourceBundle> bundle = new SimpleObjectProperty<>();
    private Locale currentLocale;

    /**
     * Constructor privado que carga la preferencia de idioma guardada
     * e inicializa el {@link ResourceBundle} correspondiente.
     */
    private LanguageManager() {
        currentLocale = loadLocalePreference();
        bundle.set(ResourceBundle.getBundle(BUNDLE_BASE, currentLocale));
    }

    /**
     * Devuelve la instancia única del singleton.
     *
     * @return instancia global de {@link LanguageManager}
     */
    public static LanguageManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Acceso al bundle y locale
    // -------------------------------------------------------------------------

    /**
     * Devuelve el {@link ResourceBundle} del idioma activo.
     *
     * @return bundle de recursos actual
     */
    public ResourceBundle getBundle() {
        return bundle.get();
    }

    /**
     * Devuelve la propiedad observable del bundle, útil para registrar
     * listeners que reaccionen al cambio de idioma en tiempo real.
     *
     * @return propiedad observable del {@link ResourceBundle}
     */
    public ObjectProperty<ResourceBundle> bundleProperty() {
        return bundle;
    }

    /**
     * Devuelve la cadena localizada correspondiente a la clave indicada.
     * Si la clave no existe, devuelve {@code "?<clave>?"} para facilitar
     * la detección de traducciones ausentes durante el desarrollo.
     *
     * @param key clave de localización
     * @return cadena localizada, o {@code "?<clave>?"} si no se encuentra
     */
    public String get(String key) {
        try {
            return bundle.get().getString(key);
        } catch (Exception e) {
            // Marcamos las claves ausentes con ? para detectarlas fácilmente en la UI
            return "?" + key + "?";
        }
    }

    /**
     * Devuelve el locale actualmente activo.
     *
     * @return locale actual
     */
    public Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Cambia el idioma activo actualizando el locale y recargando el bundle.
     * Los listeners registrados en {@link #bundleProperty()} serán notificados
     * automáticamente gracias a la propiedad observable.
     *
     * @param locale nuevo locale a aplicar
     */
    public void setLocale(Locale locale) {
        currentLocale = locale;
        bundle.set(ResourceBundle.getBundle(BUNDLE_BASE, locale));
    }

    /**
     * Indica si el idioma activo es el español.
     *
     * @return {@code true} si el locale actual es {@code "es"}
     */
    public boolean isSpanish() {
        return currentLocale.getLanguage().equals("es");
    }

    // -------------------------------------------------------------------------
    // Persistencia de preferencias
    // -------------------------------------------------------------------------

    /**
     * Persiste la preferencia de idioma en el fichero de configuración local.
     * Lee las propiedades existentes antes de escribir para no sobreescribir
     * otros valores como el tema visual.
     *
     * @param locale locale a guardar
     */
    public void saveLocalePreference(Locale locale) {
        try {
            File file = new File(CONFIG_PATH);
            // Creamos el directorio .taskmaster si no existe y verificamos que se creó
            File parentDir = file.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                log.error("No se pudo crear el directorio de configuración: {}", parentDir.getAbsolutePath());
                return;
            }

            Properties props = new Properties();

            // Cargamos las propiedades existentes para no perder otros ajustes
            if (file.exists()) {
                try (FileInputStream in = new FileInputStream(file)) {
                    props.load(in);
                }
            }

            props.setProperty("language", locale.getLanguage());

            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "TaskMaster config");
            }
        } catch (Exception e) {
            log.error("No se pudo guardar la preferencia de idioma: {}", e.getMessage());
        }
    }

    /**
     * Carga la preferencia de idioma desde el fichero de configuración local.
     * Devuelve español por defecto si el fichero no existe o no puede leerse.
     *
     * @return locale guardado, o {@code Locale.of("es")} por defecto
     */
    public Locale loadLocalePreference() {
        try {
            File file = new File(CONFIG_PATH);
            if (!file.exists()) return Locale.of("es");

            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            }

            String lang = props.getProperty("language", "es");
            return Locale.of(lang, lang.equals("es") ? "ES" : "US");
        } catch (Exception e) {
            // Si falla la lectura usamos español como fallback seguro
            log.warn("No se pudo cargar la preferencia de idioma, usando español por defecto");
            return Locale.of("es", "ES");
        }
    }
}