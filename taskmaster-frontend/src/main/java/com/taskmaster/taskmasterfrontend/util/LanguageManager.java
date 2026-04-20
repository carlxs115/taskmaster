package com.taskmaster.taskmasterfrontend.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Locale;
import java.util.ResourceBundle;

public class LanguageManager {

    private static final LanguageManager INSTANCE = new LanguageManager();
    private static final String BUNDLE_BASE = "com.taskmaster.taskmasterfrontend.i18n.messages";

    private final ObjectProperty<ResourceBundle> bundle = new SimpleObjectProperty<>();
    private Locale currentLocale = new Locale("es");

    private LanguageManager() {
        bundle.set(ResourceBundle.getBundle(BUNDLE_BASE, currentLocale));
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
