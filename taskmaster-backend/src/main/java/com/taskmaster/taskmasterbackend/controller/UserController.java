package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.response.UserResponse;
import com.taskmaster.taskmasterbackend.dto.response.UserStatsResponse;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.AvatarService;
import com.taskmaster.taskmasterbackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;
    private final SecurityUtils securityUtils;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        User user = userService.findById(userId);
        return ResponseEntity.ok(UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                .hasAvatar(user.getAvatarPath() != null)
                .build());
    }

    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userService.getStats(userId));
    }

    // ---------- AVATAR ----------

    /**
     * Sube o reemplaza el avatar del usuario autenticado.
     * Espera un fichero multipart con la clave "file".
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadMyAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = securityUtils.getUserId(userDetails);
        User user = avatarService.uploadAvatar(userId, file);
        return ResponseEntity.ok(UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                .hasAvatar(true)
                .build());
    }

    /**
     * Devuelve el avatar del usuario autenticado como bytes (image/png o image/jpeg).
     * 404 si no tiene avatar configurado.
     */
    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> getMyAvatar(
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Long userId = securityUtils.getUserId(userDetails);
        return buildAvatarResponse(userId);
    }

    /**
     * Devuelve el avatar de cualquier usuario (para mostrarlo en la UI cuando se asigna una tarea, etc.).
     * Requiere autenticación pero no que seas tú mismo.
     */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getUserAvatar(@PathVariable Long id) throws IOException {
        return buildAvatarResponse(id);
    }

    /**
     * Elimina el avatar del usuario autenticado (vuelve a iniciales/placeholder).
     */
    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteMyAvatar(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        avatarService.deleteAvatar(userId);
        return ResponseEntity.noContent().build();
    }

    /** Helper para construir la respuesta HTTP con los bytes del avatar o 404 si no existe. */
    private ResponseEntity<byte[]> buildAvatarResponse(Long userId) throws IOException {
        AvatarService.AvatarData data = avatarService.getAvatar(userId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Content-Type", data.contentType())
                .header("Cache-Control", "no-cache") // el cliente debería re-pedir tras subida
                .body(data.bytes());
    }
}
