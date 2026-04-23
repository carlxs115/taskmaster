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
        SAKURA,
        PERLA,
        ARTICO,
        NOCHE,
        VIGILANTE,
        HACKER,
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
                || currentTheme.get() == Theme.VIGILANTE
                || currentTheme.get() == Theme.HACKER;
    }

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

    public String getCssFileNamePublic() {
        return getCssFileName(currentTheme.get());
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
