package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.response.UserSettingsResponse;
import com.taskmaster.taskmasterbackend.model.UserSettings;
import com.taskmaster.taskmasterbackend.model.enums.ThemeType;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST que gestiona los ajustes del usuario autenticado.
 *
 * <p>Permite consultar y actualizar las preferencias del usuario,
 * como el periodo de retención de la papelera y el tema visual.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/settings
     * Devuelve la configuración actual del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con la configuración del usuario
     */
    @GetMapping
    public ResponseEntity<UserSettingsResponse> getSettings(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponse(userSettingsService.getSettingsByUserId(userId)));
    }

    /**
     * PATCH /api/settings/trash-retention
     * Actualiza el periodo de retención de la papelera del usuario autenticado.
     * Solo se aceptan los valores 7, 15 o 30 días.
     *
     * @param days        nuevo periodo en días (7, 15 o 30)
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con la configuración actualizada
     */
    @PatchMapping("/trash-retention")
    public ResponseEntity<UserSettingsResponse> updateTrashRetention(
            @RequestParam int days,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponse(userSettingsService.updateTrashRetention(userId, days)));
    }

    /**
     * PATCH /api/settings/theme
     * Actualiza el tema visual del usuario autenticado.
     * Spring rechaza automáticamente valores de tema inválidos
     * al deserializar el enum {@link ThemeType}.
     *
     * @param theme       nuevo tema a aplicar
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con la configuración actualizada
     */
    @PatchMapping("/theme")
    public ResponseEntity<UserSettingsResponse> updateTheme(
            @RequestParam ThemeType theme,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponse(userSettingsService.updateTheme(userId, theme)));
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Convierte la entidad {@link UserSettings} al DTO de respuesta {@link UserSettingsResponse}.
     * Evita exponer campos internos como el {@code id} de la configuración
     * o la referencia al usuario.
     *
     * @param s entidad de configuración a convertir
     * @return DTO con solo los campos relevantes para el cliente
     */
    private UserSettingsResponse toResponse(UserSettings s) {
        return new UserSettingsResponse(s.getTrashRetentionDays(), s.getTheme());
    }
}
