package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.UserSettings;
import com.taskmaster.taskmasterbackend.model.enums.ThemeType;
import com.taskmaster.taskmasterbackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * <p>La configuración se crea automáticamente al registrar al usuario,
     * por lo que en condiciones normales siempre existirá.</p>
     *
     * @param userId identificador del usuario
     * @return configuración del usuario
     * @throws ResourceNotFoundException si no existe configuración para ese usuario
     */
    public UserSettings getSettingsByUserId(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Configuración no encontrada para el usuario con id: " + userId));
    }

    /**
     * Actualiza el periodo de retención de elementos en la papelera.
     *
     * <p>Solo se aceptan los valores 7, 15 o 30 días, que corresponden
     * a las opciones disponibles en la pantalla de ajustes.</p>
     *
     * @param userId        identificador del usuario
     * @param retentionDays nuevo periodo de retención en días (7, 15 o 30)
     * @return configuración actualizada
     * @throws BusinessException si el valor no es 7, 15 ni 30
     */
    @Transactional
    public UserSettings updateTrashRetention(Long userId, int retentionDays) {
        // Validamos que el valor sea uno de los tres permitidos por la interfaz
        if (retentionDays != 7 && retentionDays != 15 && retentionDays != 30) {
            throw new BusinessException("El periodo de retención debe ser 7, 15 o 30 días");
        }

        UserSettings settings = getSettingsByUserId(userId);
        settings.setTrashRetentionDays(retentionDays);
        return userSettingsRepository.save(settings);
    }

    /**
     * Actualiza el tema visual seleccionado por el usuario.
     * El tema se aplica inmediatamente en el frontend JavaFX al recibir la respuesta.
     *
     * @param userId identificador del usuario
     * @param theme  nuevo tema a aplicar
     * @return configuración actualizada
     */
    @Transactional
    public UserSettings updateTheme(Long userId, ThemeType theme) {
        // No validamos el ThemeType porque al ser un enum Java,
        // Spring ya rechaza valores inválidos en la deserialización JSON
        UserSettings settings = getSettingsByUserId(userId);
        settings.setTheme(theme);
        return userSettingsRepository.save(settings);
    }
}
