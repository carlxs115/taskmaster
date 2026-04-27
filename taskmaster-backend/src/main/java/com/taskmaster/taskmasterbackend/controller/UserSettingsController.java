package com.taskmaster.taskmasterbackend.controller;

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
     * Devuelve la configuración del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return configuración del usuario
     */
    @GetMapping
    public ResponseEntity<UserSettings> getSettings(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userSettingsService.getSettingsByUserId(userId));
    }

    /**
     * PATCH /api/settings/trash-retention
     * Actualiza el periodo de retención de la papelera del usuario autenticado.
     *
     * @param days        nuevo periodo en días (7, 15 o 30)
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return configuración actualizada
     */
    @PatchMapping("/trash-retention")
    public ResponseEntity<UserSettings> updateTrashRetention(
            @RequestParam int days,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userSettingsService.updateTrashRetention(userId, days));
    }

    /**
     * PATCH /api/settings/theme
     * Actualiza el tema visual del usuario autenticado.
     *
     * @param theme       nuevo tema a aplicar
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return configuración actualizada
     */
    @PatchMapping("/theme")
    public ResponseEntity<UserSettings> updateTheme(
            @RequestParam ThemeType theme,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userSettingsService.updateTheme(userId, theme));
    }
}
