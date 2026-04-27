package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Singleton que gestiona el tema visual de la aplicación.
 *
 * <p>Controla qué CSS se aplica al {@link javafx.scene.Scene} principal en cada momento,
 * permitiendo cambiar el tema en caliente sin reiniciar la aplicación. La preferencia
 * se persiste localmente en {@code ~/.taskmaster/config.properties} y también
 * se sincroniza con el backend tras el login.</p>
 *
 * @author Carlos
 */
public class ThemeManager {

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
        LUZ
    }

    private static final ThemeManager INSTANCE = new ThemeManager();

    /** Ruta al fichero de configuración local donde se persiste la preferencia de tema. */
    private static final String CONFIG_PATH =
            System.getProperty("user.home") + "/.taskmaster/config.properties";

    /** Ruta base dentro del classpath donde se ubican los ficheros CSS de los temas. */
    private static final String CSS_BASE =
            "/com/taskmaster/taskmasterfrontend/themes/";

    /** Propiedad observable que almacena el tema actualmente activo. */
    private final ObjectProperty<Theme> currentTheme = new SimpleObjectProperty<>();

    /** Scene principal de la aplicación sobre el que se aplican los estilos. */
    private Scene mainScene;

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
     * Siempre carga primero el CSS base (Amatista) y luego, si el tema es distinto,
     * añade el CSS específico del tema encima.
     *
     * @param theme el tema a aplicar
     */
    public void applyTheme(Theme theme) {
        currentTheme.set(theme);
        saveThemePreference(theme);
        if (mainScene == null) return;

        mainScene.getStylesheets().clear();

        String baseUrl = getClass().getResource(CSS_BASE + "theme-amatista.css") != null
                ? getClass().getResource(CSS_BASE + "theme-amatista.css").toExternalForm()
                : null;
        if (baseUrl != null) {
            mainScene.getStylesheets().add(baseUrl);
            System.out.println("Base CSS cargado: " + baseUrl);
        }

        if (theme != Theme.AMATISTA) {
            String themeUrl = getClass().getResource(CSS_BASE + getCssFileName(theme)) != null
                    ? getClass().getResource(CSS_BASE + getCssFileName(theme)).toExternalForm()
                    : null;
            if (themeUrl != null) {
                mainScene.getStylesheets().add(themeUrl);
                System.out.println("Tema CSS cargado: " + themeUrl);
            } else {
                System.out.println("ERROR: no se encontró el CSS para " + theme);
            }
        }

        System.out.println("Stylesheets activos: " + mainScene.getStylesheets());
    }

    /**
     * Devuelve el tema actualmente activo.
     *
     * @return tema actual
     */
    public Theme getCurrentTheme() {
        return currentTheme.get();
    }

    /**
     * Devuelve la propiedad observable del tema actual, útil para añadir listeners.
     *
     * @return propiedad observable del tema
     */
    public ObjectProperty<Theme> themeProperty() {
        return currentTheme;
    }

    /**
     * Indica si el tema activo tiene fondo oscuro.
     *
     * @return {@code true} si el tema es oscuro, {@code false} si es claro
     */
    public boolean isDark() {
        return currentTheme.get() == Theme.AMATISTA_DARK
                || currentTheme.get() == Theme.AURORA_BOREALIS
                || currentTheme.get() == Theme.OCEANO
                || currentTheme.get() == Theme.NOCHE
                || currentTheme.get() == Theme.VIGILANTE
                || currentTheme.get() == Theme.HACKER;
    }

    /**
     * Devuelve el color de fondo principal de la aplicación según el tema activo,
     * en formato hexadecimal CSS.
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
            default              -> "#f0f0f5";
        };
    }

    /**
     * Devuelve el nombre del fichero CSS correspondiente al tema indicado.
     *
     * @param theme el tema del que se quiere obtener el fichero CSS
     * @return nombre del fichero CSS (sin ruta)
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
            case LUZ               -> "theme-luz.css";
        };
    }

    /**
     * Devuelve el nombre del fichero CSS del tema actualmente activo.
     * Versión pública de {@link #getCssFileName(Theme)}.
     *
     * @return nombre del fichero CSS del tema actual
     */
    public String getCssFileNamePublic() {
        return getCssFileName(currentTheme.get());
    }

    /**
     * Persiste la preferencia de tema en el fichero de configuración local.
     * Si el fichero ya contiene otras propiedades (como el idioma), las conserva.
     *
     * @param theme el tema a guardar
     */
    public void saveThemePreference(Theme theme) {
        try {
            File file = new File(CONFIG_PATH);
            file.getParentFile().mkdirs();
            Properties props = new Properties();
            // Leer props existentes para no sobreescribir idioma, etc.
            if (file.exists()) {
                try (var in = new FileInputStream(file)) {
                    props.load(in);
                }
            }
            props.setProperty("theme", theme.name());
            try (var out = new FileOutputStream(file)) {
                props.store(out, "TaskMaster config");
            }
        } catch (Exception e) {
            System.err.println("No se pudo guardar la preferencia de tema: " + e.getMessage());
        }
    }

    /**
     * Carga la preferencia de tema desde el fichero de configuración local.
     * Si el fichero no existe o el valor no es válido, devuelve {@link Theme#AMATISTA}.
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
            return Theme.AMATISTA;
        }
    }

    /**
     * Convierte un {@code String} recibido del backend al enum {@link Theme} correspondiente.
     * Si el valor no coincide con ningún tema, devuelve {@link Theme#AMATISTA} por defecto.
     *
     * @param value nombre del tema en texto
     * @return tema correspondiente, o {@code AMATISTA} si no se reconoce
     */
    public static Theme fromString(String value) {
        try {
            return Theme.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return Theme.AMATISTA;
        }
    }
}
