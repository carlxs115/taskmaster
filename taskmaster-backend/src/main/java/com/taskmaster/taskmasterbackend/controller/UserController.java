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
 * solo puede acceder y modificar sus propios datos, ya que el {@code userId}
 * se extrae siempre del token de autenticación, nunca del request.</p>
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

    // -------------------------------------------------------------------------
    // Perfil
    // -------------------------------------------------------------------------

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
        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * GET /api/users/stats
     * Devuelve las estadísticas de actividad del usuario autenticado
     * (total de tareas, completadas, pendientes, tasa de completado, etc.).
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con las estadísticas del usuario
     */
    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(userService.getStats(userId));
    }

    // -------------------------------------------------------------------------
    // Avatar
    // -------------------------------------------------------------------------

    /**
     * POST /api/users/me/avatar
     * Sube o reemplaza la foto de perfil del usuario autenticado.
     * Espera un fichero multipart con la clave {@code "file"}.
     * Si ya tenía avatar, el fichero anterior se elimina del disco.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @param file        fichero de imagen (PNG o JPEG, máximo 2 MB)
     * @return 200 OK con los datos del usuario actualizados
     * @throws IOException si ocurre un error al guardar el fichero en disco
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadMyAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = securityUtils.getUserId(userDetails);
        // uploadAvatar devuelve el usuario ya actualizado con el nuevo avatarPath
        User user = avatarService.uploadAvatar(userId, file);
        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * GET /api/users/me/avatar
     * Devuelve la foto de perfil del usuario autenticado como bytes binarios.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con la imagen, o 404 si el usuario no tiene avatar
     * @throws IOException si ocurre un error al leer el fichero del disco
     */
    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> getMyAvatar(
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Long userId = securityUtils.getUserId(userDetails);
        return buildAvatarResponse(userId);
    }

    /**
     * GET /api/users/{id}/avatar
     * Devuelve la foto de perfil de cualquier usuario por su identificador.
     * Requiere autenticación pero no necesariamente ser el propio usuario,
     * ya que los avatares son datos no sensibles (solo imagen de perfil).
     *
     * @param id identificador del usuario cuyo avatar se quiere obtener
     * @return 200 OK con la imagen, o 404 si el usuario no tiene avatar
     * @throws IOException si ocurre un error al leer el fichero del disco
     */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getUserAvatar(@PathVariable Long id) throws IOException {
        return buildAvatarResponse(id);
    }

    /**
     * DELETE /api/users/me/avatar
     * Elimina la foto de perfil del usuario autenticado del disco y de la base de datos.
     * El frontend volverá a mostrar las iniciales del usuario como placeholder.
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

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Construye la respuesta HTTP con los bytes del avatar del usuario indicado.
     * Incluye la cabecera {@code Cache-Control: no-cache} para que el cliente
     * siempre pida la imagen actualizada tras una subida.
     *
     * @param userId identificador del usuario
     * @return 200 OK con la imagen y su Content-Type, o 404 si no tiene avatar
     * @throws IOException si ocurre un error al leer el fichero del disco
     */
    private ResponseEntity<byte[]> buildAvatarResponse(Long userId) throws IOException {
        AvatarService.AvatarData data = avatarService.getAvatar(userId);

        // Si el usuario no tiene avatar devolvemos 404 para que el frontend
        // muestre el placeholder de iniciales
        if (data == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .header("Content-Type", data.contentType())
                // no-cache obliga al cliente a re-pedir la imagen tras cada subida,
                // evitando que muestre el avatar anterior desde caché
                .header("Cache-Control", "no-cache")
                .body(data.bytes());
    }

    /**
     * Convierte una entidad {@link User} al DTO de respuesta {@link UserResponse}.
     * Centraliza la construcción para evitar duplicar el builder en cada endpoint.
     * La contraseña nunca se incluye en la respuesta.
     *
     * @param user entidad usuario a convertir
     * @return DTO con los datos del usuario listos para serializar a JSON
     */
    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                // hasAvatar evita exponer la ruta del fichero al cliente;
                // el frontend usa este booleano para decidir si pedir el avatar
                .hasAvatar(user.getAvatarPath() != null)
                .build();
    }
}
