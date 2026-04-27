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

/**
 * Controlador REST que gestiona el perfil y el avatar del usuario autenticado.
 *
 * <p>Todos los endpoints requieren autenticación. El usuario autenticado
 * solo puede acceder y modificar sus propios datos y avatar.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/users/me
     * Devuelve los datos del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return datos del usuario
     */
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

    /**
     * GET /api/users/stats
     * Devuelve las estadísticas de actividad del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return estadísticas del usuario
     */
    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userService.getStats(userId));
    }

    /**
     * POST /api/users/me/avatar
     * Sube o reemplaza la foto de perfil del usuario autenticado.
     * Espera un fichero multipart con la clave {@code "file"}.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @param file        fichero de imagen (PNG o JPEG, máximo 2 MB)
     * @return datos del usuario actualizados
     * @throws IOException si ocurre un error al guardar el fichero
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
     * GET /api/users/me/avatar
     * Devuelve la foto de perfil del usuario autenticado como bytes.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return imagen en formato PNG o JPEG, o 404 si no tiene avatar
     * @throws IOException si ocurre un error al leer el fichero
     */
    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> getMyAvatar(
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Long userId = securityUtils.getUserId(userDetails);
        return buildAvatarResponse(userId);
    }

    /**
     * GET /api/users/{id}/avatar
     * Devuelve la foto de perfil de cualquier usuario.
     * Requiere autenticación pero no necesariamente ser el propio usuario.
     *
     * @param id identificador del usuario
     * @return imagen en formato PNG o JPEG, o 404 si no tiene avatar
     * @throws IOException si ocurre un error al leer el fichero
     */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getUserAvatar(@PathVariable Long id) throws IOException {
        return buildAvatarResponse(id);
    }

    /**
     * DELETE /api/users/me/avatar
     * Elimina la foto de perfil del usuario autenticado.
     * El usuario volverá a mostrar sus iniciales como placeholder.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteMyAvatar(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        avatarService.deleteAvatar(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Construye la respuesta HTTP con los bytes del avatar del usuario indicado.
     *
     * @param userId identificador del usuario
     * @return respuesta con la imagen y su Content-Type, o 404 si no tiene avatar
     * @throws IOException si ocurre un error al leer el fichero
     */
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
