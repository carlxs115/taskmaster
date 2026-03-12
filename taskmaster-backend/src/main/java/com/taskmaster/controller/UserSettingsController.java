package com.taskmaster.controller;

import com.taskmaster.model.UserSettings;
import com.taskmaster.security.SecurityUtils;
import com.taskmaster.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * USERSETTINGSCONTROLLER
 *
 * Gestiona los ajustes del usuario autenticado.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<UserSettings> getSettings(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userSettingsService.getSettingsByUserId(userId));
    }

    @PatchMapping("/trash-retention")
    public ResponseEntity<UserSettings> updateTrashRetention(
            @RequestParam int days,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userSettingsService.updateTrashRetention(userId, days));
    }
}
