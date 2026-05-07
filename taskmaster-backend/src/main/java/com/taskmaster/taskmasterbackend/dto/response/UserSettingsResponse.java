package com.taskmaster.taskmasterbackend.dto.response;

import com.taskmaster.taskmasterbackend.model.enums.ThemeType;

/**
 * DTO de respuesta con las preferencias del usuario.
 *
 * <p>Evita exponer la entidad {@link com.taskmaster.taskmasterbackend.model.UserSettings}
 * directamente en la API, ocultando campos internos como el {@code id}
 * o la referencia al usuario.</p>
 *
 * @param trashRetentionDays días de retención configurados para la papelera (7, 15 o 30)
 * @param theme              tema visual seleccionado por el usuario
 *
 * @author Carlos
 */
public record UserSettingsResponse(
        int trashRetentionDays,
        ThemeType theme
) {}
