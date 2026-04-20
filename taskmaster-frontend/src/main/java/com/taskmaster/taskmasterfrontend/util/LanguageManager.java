package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Locale;
import java.util.ResourceBundle;

public class LanguageManager {

    private static final LanguageManager INSTANCE = new LanguageManager();
    private static final String BUNDLE_BASE = "com.taskmaster.taskmasterfrontend.i18n.messages";
    private static final String CONFIG_PATH =
            System.getProperty("user.home") + "/.taskmaster/config.properties";

    private final ObjectProperty<ResourceBundle> bundle = new SimpleObjectProperty<>();
    private Locale currentLocale = new Locale("es");

    private LanguageManager() {
        currentLocale = loadLocalePreference();
        bundle.set(ResourceBundle.getBundle(BUNDLE_BASE, currentLocale));
    }

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

    public static LanguageManager getInstance() {
        return INSTANCE;
    }

    public ResourceBundle getBundle() {
        return bundle.get();
    }

    public ObjectProperty<ResourceBundle> bundleProperty() {
        return bundle;
    }

    public String get(String key) {
        try {
            return bundle.get().getString(key);
        } catch (Exception e) {
            return "?" + key + "?";
        }
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public void setLocale(Locale locale) {
        currentLocale = locale;
        bundle.set(ResourceBundle.getBundle(BUNDLE_BASE, locale));
    }

    public boolean isSpanish() {
        return currentLocale.getLanguage().equals("es");
    }
}
