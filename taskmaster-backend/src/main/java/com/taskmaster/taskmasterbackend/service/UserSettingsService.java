package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.model.UserSettings;
import com.taskmaster.taskmasterbackend.model.enums.ThemeType;
import com.taskmaster.taskmasterbackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * Servicio que gestiona las preferencias personales de cada usuario.
 *
 * <p>Proporciona operaciones para consultar y actualizar los ajustes del usuario,
 * como el periodo de retención de la papelera o el tema visual seleccionado.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;

    /**
     * Obtiene la configuración de un usuario por su identificador.
     *
     * @param userId identificador del usuario
     * @return configuración del usuario
     * @throws RuntimeException si no existe configuración para ese usuario
     */
    public UserSettings getSettingsByUserId(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada para el usuario: " + userId));
    }

    /**
     * Actualiza el periodo de retención de elementos en la papelera.
     * Solo se aceptan los valores 7, 15 o 30 días.
     *
     * @param userId        identificador del usuario
     * @param retentionDays nuevo periodo de retención en días (7, 15 o 30)
     * @return configuración actualizada
     * @throws RuntimeException si el valor no es 7, 15 ni 30
     */
    public UserSettings updateTrashRetention(Long userId, int retentionDays) {

        if (retentionDays != 7 && retentionDays != 15 && retentionDays != 30) {
            throw new RuntimeException("El periodo de retención debe ser 7, 15 o 30 días");
        }

        UserSettings settings = getSettingsByUserId(userId);
        settings.setTrashRetentionDays(retentionDays);
        return userSettingsRepository.save(settings);
    }

    /**
     * Actualiza el tema visual del usuario.
     *
     * @param userId identificador del usuario
     * @param theme  nuevo tema a aplicar
     * @return configuración actualizada
     */
    public UserSettings updateTheme(Long userId, ThemeType theme) {
        UserSettings settings = getSettingsByUserId(userId);
        settings.setTheme(theme);
        return userSettingsRepository.save(settings);
    }
}
