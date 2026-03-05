package com.taskmaster.service;

import com.taskmaster.model.UserSettings;
import com.taskmaster.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * SERVICIO DE USERSETTINGS
 *
 * Gestiona las preferencias del usuario.
 * Por ahora gestiona el periodo de retención de la papelera,
 * pero está preparado para añadir más ajustes en el futuro
 * (tema visual, idioma, notificaciones, etc.)
 */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;

    /**
     * Obtiene la configuración de un usuario.
     *
     * @throws RuntimeException si no existe configuración para ese usuario
     */
    public UserSettings getSettingsByUserId(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada para el usuario: " + userId));
    }

    public UserSettings updateTrashRetention(Long userId, int retentionDays) {

        // Validamos que el valor sea uno de los permitidos
        if (retentionDays != 7 && retentionDays != 15 && retentionDays != 30) {
            throw new RuntimeException("El periodo de retención debe ser 7, 15 o 30 días");
        }

        UserSettings settings = getSettingsByUserId(userId);
        settings.setTrashRetentionDays(retentionDays);
        return userSettingsRepository.save(settings);
    }
}
