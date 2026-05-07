package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/**
 * Singleton que gestiona el tema visual de la aplicación.
 *
 * <p>Controla qué CSS se aplica al {@link javafx.scene.Scene} principal en cada momento,
 * permitiendo cambiar el tema en caliente sin reiniciar la aplicación. La preferencia
 * se persiste localmente en {@code ~/.taskmaster/config.properties} y también
 * se sincroniza con el backend tras el login.</p>
 *
 * <p><b>Nota de concurrencia:</b> el singleton se inicializa de forma eager
 * ({@code static final}), lo que garantiza thread-safety gracias al mecanismo
 * de inicialización de clases de la JVM.</p>
 *
 * @author Carlos
 */
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    // Singleton eager — thread-safe por garantía de la JVM
    private static final ThemeManager INSTANCE = new ThemeManager();

    /** Ruta al fichero de configuración local donde se persiste la preferencia de tema. */
    private static final String CONFIG_PATH =
            System.getProperty("user.home") + "/.taskmaster/config.properties";

    /** Ruta base dentro del classpath donde se ubican los ficheros CSS de los temas. */
    private static final String CSS_BASE =
            "/com/taskmaster/taskmasterfrontend/themes/";

    /**
     * Conjunto de temas con fondo oscuro, usado en {@link #isDark()}.
     * Centralizado aquí para evitar cadenas de comparaciones con {@code ==}.
     */
    private static final Set<Theme> DARK_THEMES = EnumSet.of(
            Theme.AMATISTA_DARK,
            Theme.AURORA_BOREALIS,
            Theme.OCEANO,
            Theme.NOCHE,
            Theme.VIGILANTE,
            Theme.HACKER,
            Theme.DRAGONSLAYER,
            Theme.LUZ
    );

    /** Propiedad observable que almacena el tema actualmente activo. */
    private final ObjectProperty<Theme> currentTheme = new SimpleObjectProperty<>();

    /** Scene principal de la aplicación sobre el que se aplican los estilos. */
    private Scene mainScene;

    /**
     * Enumeración de los temas visuales disponibles en la aplicación.
     */
    public enum Theme {
        AMATISTA,
        AMATISTA_DARK,
        AURORA_BOREALIS,
        OCEANO,
        PRADERA,
        AMBAR,
        SAKURA,
        PERLA,
        ARTICO,
        NOCHE,
        VIGILANTE,
        HACKER,
        DRAGONSLAYER,
        LUZ
    }

    /**
     * Constructor privado que carga la preferencia de tema guardada localmente.
     */
    private ThemeManager() {
        currentTheme.set(loadThemePreference());
    }

    /**
     * Devuelve la instancia única del {@code ThemeManager}.
     *
     * @return instancia singleton
     */
    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Gestión del Scene y aplicación de temas
    // -------------------------------------------------------------------------

    /**
     * Registra el Scene principal de la aplicación.
     * Debe llamarse una sola vez desde {@code MainApp} al iniciar la app
     * y cada vez que se navega a la pantalla principal.
     *
     * @param scene el Scene principal sobre el que se aplicarán los temas
     */
    public void setMainScene(Scene scene) {
        this.mainScene = scene;
        applyTheme(currentTheme.get());
    }

    /**
     * Aplica el tema indicado al Scene principal reemplazando el CSS anterior.
     * Siempre carga primero el CSS base (Amatista) con las variables globales
     * y luego, si el tema es distinto, añade el CSS específico del tema encima
     * para sobreescribir solo las variables que cambian.
     *
     * @param theme el tema a aplicar
     */
    public void applyTheme(Theme theme) {
        currentTheme.set(theme);
        saveThemePreference(theme);

        if (mainScene == null) return;

        mainScene.getStylesheets().clear();

        // Cargamos el CSS base (Amatista) que define todas las variables CSS globales
        var baseResource = getClass().getResource(CSS_BASE + "theme-amatista.css");
        if (baseResource != null) {
            mainScene.getStylesheets().add(baseResource.toExternalForm());
            log.debug("CSS base cargado: {}", baseResource.toExternalForm());
        } else {
            log.error("No se encontró el CSS base theme-amatista.css");
        }

        // Si el tema elegido no es Amatista, añadimos su CSS encima del base
        if (theme != Theme.AMATISTA) {
            var themeResource = getClass().getResource(CSS_BASE + getCssFileName(theme));
            if (themeResource != null) {
                mainScene.getStylesheets().add(themeResource.toExternalForm());
                log.debug("CSS de tema cargado: {}", themeResource.toExternalForm());
            } else {
                log.error("No se encontró el CSS para el tema: {}", theme);
            }
        }

        log.debug("Stylesheets activos: {}", mainScene.getStylesheets());
    }

    // -------------------------------------------------------------------------
    // Consultas del tema activo
    // -------------------------------------------------------------------------

    /**
     * Devuelve el tema actualmente activo.
     *
     * @return tema actual
     */
    public Theme getCurrentTheme() {
        return currentTheme.get();
    }

    /**
     * Devuelve la propiedad observable del tema actual,
     * útil para añadir listeners que reaccionen al cambio de tema.
     *
     * @return propiedad observable del tema
     */
    public ObjectProperty<Theme> themeProperty() {
        return currentTheme;
    }

    /**
     * Indica si el tema activo tiene fondo oscuro.
     * Se usa para adaptar colores de elementos que no están completamente
     * controlados por CSS, como el avatar generado dinámicamente.
     *
     * @return {@code true} si el tema es oscuro, {@code false} si es claro
     */
    public boolean isDark() {
        return DARK_THEMES.contains(currentTheme.get());
    }

    /**
     * Devuelve el color de fondo principal de la aplicación según el tema activo,
     * en formato hexadecimal CSS. Se usa para elementos pintados dinámicamente
     * desde Java que no se pueden controlar solo con CSS.
     *
     * @return color de fondo en formato {@code "#rrggbb"}
     */
    public String getBgApp() {
        return switch (currentTheme.get()) {
            case AMATISTA_DARK   -> "#0d0b1a";
            case AURORA_BOREALIS -> "#0d1520";
            case OCEANO          -> "#0a1628";
            case NOCHE           -> "#080808";
            case VIGILANTE       -> "#0a0a00";
            case HACKER          -> "#020b02";
            case LUZ             -> "#000000";
            default              -> "#f0f0f5"; // temas claros
        };
    }

    /**
     * Devuelve el nombre del fichero CSS del tema activo, para uso desde
     * los controladores que necesitan aplicar el tema a sus propias escenas.
     *
     * @return nombre del fichero CSS del tema actualmente activo
     */
    public String getCssFileNamePublic() {
        return getCssFileName(currentTheme.get());
    }

    /**
     * Convierte un {@code String} recibido del backend al enum {@link Theme} correspondiente.
     * Si el valor no coincide con ningún tema conocido, devuelve {@link Theme#AMATISTA}.
     *
     * @param value nombre del tema en texto (insensible a mayúsculas)
     * @return tema correspondiente, o {@code AMATISTA} si no se reconoce
     */
    public static Theme fromString(String value) {
        try {
            return Theme.valueOf(value.toUpperCase());
        } catch (Exception e) {
            // Si el backend devuelve un tema desconocido usamos el por defecto
            log.warn("Tema desconocido recibido del backend: '{}', usando AMATISTA", value);
            return Theme.AMATISTA;
        }
    }

    // -------------------------------------------------------------------------
    // Persistencia de preferencias
    // -------------------------------------------------------------------------

    /**
     * Persiste la preferencia de tema en el fichero de configuración local.
     * Lee las propiedades existentes antes de escribir para no sobreescribir
     * otros valores como el idioma.
     *
     * @param theme el tema a guardar
     */
    public void saveThemePreference(Theme theme) {
        try {
            File file = new File(CONFIG_PATH);
            File parentDir = file.getParentFile();

            // Verificamos que el directorio existe o se puede crear
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                log.error("No se pudo crear el directorio de configuración: {}",
                        parentDir.getAbsolutePath());
                return;
            }

            Properties props = new Properties();

            // Cargamos las propiedades existentes para no perder el idioma u otros ajustes
            if (file.exists()) {
                try (var in = new FileInputStream(file)) {
                    props.load(in);
                }
            }

            props.setProperty("theme", theme.name());

            try (var out = new FileOutputStream(file)) {
                props.store(out, "TaskMaster config");
            }

            log.debug("Preferencia de tema guardada: {}", theme.name());

        } catch (Exception e) {
            log.error("No se pudo guardar la preferencia de tema: {}", e.getMessage());
        }
    }

    /**
     * Carga la preferencia de tema desde el fichero de configuración local.
     * Si el fichero no existe o el valor guardado no es válido,
     * devuelve {@link Theme#AMATISTA} como valor por defecto.
     *
     * @return tema guardado, o {@code AMATISTA} por defecto
     */
    public Theme loadThemePreference() {
        try {
            File file = new File(CONFIG_PATH);
            if (!file.exists()) return Theme.AMATISTA;

            Properties props = new Properties();
            try (var in = new FileInputStream(file)) {
                props.load(in);
            }

            String name = props.getProperty("theme", "AMATISTA");
            return Theme.valueOf(name);

        } catch (Exception e) {
            // Si falla la lectura o el valor es inválido usamos el tema por defecto
            log.warn("No se pudo cargar la preferencia de tema, usando AMATISTA por defecto");
            return Theme.AMATISTA;
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Devuelve el nombre del fichero CSS correspondiente al tema indicado.
     *
     * @param theme el tema del que se quiere obtener el fichero CSS
     * @return nombre del fichero CSS sin ruta (p.ej. {@code "theme-oceano.css"})
     */
    private String getCssFileName(Theme theme) {
        return switch (theme) {
            case AMATISTA          -> "theme-amatista.css";
            case AMATISTA_DARK     -> "theme-amatista-dark.css";
            case AURORA_BOREALIS   -> "theme-aurora-borealis.css";
            case OCEANO            -> "theme-oceano.css";
            case PRADERA           -> "theme-pradera.css";
            case AMBAR             -> "theme-ambar.css";
            case SAKURA            -> "theme-sakura.css";
            case PERLA             -> "theme-perla.css";
            case ARTICO            -> "theme-artico.css";
            case NOCHE             -> "theme-noche.css";
            case VIGILANTE         -> "theme-vigilante.css";
            case HACKER            -> "theme-hacker.css";
            case DRAGONSLAYER      -> "theme-dragonslayer.css";
            case LUZ               -> "theme-luz.css";
        };
    }
}