package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * THEMEMANAGER
 *
 * Singleton que gestiona el tema visual de la aplicación.
 * Sigue el mismo patrón que LanguageManager.
 * Persiste la preferencia localmente (antes del login) y en el backend (tras login).
 */
public class ThemeManager {

    public enum Theme {
        AMATISTA,
        AMATISTA_DARK,
        AURORA_BOREALIS,
        OCEANO,
        PRADERA,
        AMBAR,
        AURORA,
        PERLA,
        ARTICO,
        NOCHE,
        VIGILANTE,
        LUZ
    }

    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final String CONFIG_PATH =
            System.getProperty("user.home") + "/.taskmaster/config.properties";
    private static final String CSS_BASE =
            "/com/taskmaster/taskmasterfrontend/themes/";

    private final ObjectProperty<Theme> currentTheme = new SimpleObjectProperty<>();
    private Scene mainScene;

    private ThemeManager() {
        currentTheme.set(loadThemePreference());
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Registra el Scene principal. Se llama desde MainApp una sola vez.
     * A partir de aquí, applyTheme() puede cambiar el CSS en caliente.
     */
    public void setMainScene(Scene scene) {
        this.mainScene = scene;
        applyTheme(currentTheme.get());
    }

    /**
     * Aplica el tema al Scene principal reemplazando el CSS anterior.
     */
    public void applyTheme(Theme theme) {
        currentTheme.set(theme);
        saveThemePreference(theme);
        if (mainScene == null) return;

        mainScene.getStylesheets().clear();
        String cssFile = CSS_BASE + getCssFileName(theme);
        String cssUrl = getClass().getResource(cssFile) != null
                ? getClass().getResource(cssFile).toExternalForm()
                : null;
        if (cssUrl != null) {
            mainScene.getStylesheets().add(cssUrl);
        }
    }

    public Theme getCurrentTheme() {
        return currentTheme.get();
    }

    public ObjectProperty<Theme> themeProperty() {
        return currentTheme;
    }

    public boolean isDark() {
        return currentTheme.get() == Theme.AMATISTA_DARK
                || currentTheme.get() == Theme.AURORA_BOREALIS
                || currentTheme.get() == Theme.OCEANO
                || currentTheme.get() == Theme.NOCHE
                || currentTheme.get() == Theme.VIGILANTE;
    }

    private String getCssFileName(Theme theme) {
        return switch (theme) {
            case AMATISTA          -> "theme-amatista.css";
            case AMATISTA_DARK     -> "theme-amatista-dark.css";
            case AURORA_BOREALIS -> "theme-aurora-borealis.css";
            case OCEANO          -> "theme-oceano.css";
            case PRADERA         -> "theme-pradera.css";
            case AMBAR           -> "theme-ambar.css";
            case AURORA          -> "theme-aurora.css";
            case PERLA           -> "theme-perla.css";
            case ARTICO          -> "theme-artico.css";
            case NOCHE           -> "theme-noche.css";
            case VIGILANTE       -> "theme-vigilante.css";
            case LUZ             -> "theme-luz.css";
        };
    }

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
     * Convierte el String del backend al enum Theme.
     * Se llama tras login cuando se cargan los settings del usuario.
     */
    public static Theme fromString(String value) {
        try {
            return Theme.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return Theme.AMATISTA;
        }
    }
}
