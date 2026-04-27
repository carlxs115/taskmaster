package com.taskmaster.taskmasterbackend.model.enums;

/**
 * Enumeración de los temas visuales disponibles en la aplicación.
 *
 * <p>El valor seleccionado por el usuario se persiste en {@link com.taskmaster.taskmasterbackend.model.UserSettings}
 * y se sincroniza con el frontend al iniciar sesión.</p>
 *
 * @author Carlos
 */
public enum ThemeType {
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
