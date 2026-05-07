package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.request.ChangePasswordRequest;
import com.taskmaster.taskmasterbackend.dto.request.LoginRequest;
import com.taskmaster.taskmasterbackend.dto.request.RegisterRequest;
import com.taskmaster.taskmasterbackend.dto.request.UpdateProfileRequest;
import com.taskmaster.taskmasterbackend.dto.response.UserResponse;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.ActivityLogService;
import com.taskmaster.taskmasterbackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST que gestiona la autenticación y el perfil de usuario.
 *
 * <p>Los endpoints {@code /register} y {@code /login} son públicos (no requieren
 * autenticación). El resto requieren Basic Auth válido.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;

    // -------------------------------------------------------------------------
    // Endpoints públicos
    // -------------------------------------------------------------------------

    /**
     * POST /api/auth/register
     * Registra un nuevo usuario en el sistema.
     * No requiere autenticación previa.
     *
     * @param request datos del nuevo usuario validados con {@code @Valid}
     * @return 201 Created con los datos del usuario creado
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getBirthDate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    /**
     * POST /api/auth/login
     * Autentica al usuario con username o email y contraseña.
     * Registra el evento de login en el historial de actividad.
     *
     * <p>El proceso de autenticación que realiza Spring Security internamente:</p>
     * <ol>
     *   <li>Llama a {@code UserDetailsServiceImpl.loadUserByUsername()}</li>
     *   <li>Compara la contraseña introducida con el hash BCrypt almacenado en BD</li>
     *   <li>Si son correctas devuelve un objeto {@code Authentication}</li>
     *   <li>Si no, lanza {@code AuthenticationException}</li>
     * </ol>
     *
     * @param request credenciales del usuario (username/email y contraseña)
     * @return 200 OK con los datos del usuario, o 401 si las credenciales son incorrectas
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Usamos el username resuelto por Spring Security, no el del request,
            // porque el usuario puede haber introducido su email en lugar del username
            String resolvedUsername = authentication.getName();
            User user = userService.findByUsername(resolvedUsername);

            // Registramos el login en el historial de actividad
            activityLogService.log(user.getId(), ActionType.LOGIN);

            return ResponseEntity.ok(toResponse(user));

        } catch (AuthenticationException e) {
            // SEGURIDAD: devolvemos 401 sin detalles para no confirmar
            // si el usuario existe o si la contraseña es incorrecta
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    // -------------------------------------------------------------------------
    // Endpoints autenticados
    // -------------------------------------------------------------------------

    /**
     * POST /api/auth/logout
     * Registra el evento de logout en el historial de actividad.
     * En HTTP Basic no hay sesión que invalidar en el servidor,
     * el cliente simplemente deja de enviar las credenciales.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        activityLogService.log(userId, ActionType.LOGOUT);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/auth/profile
     * Devuelve los datos del perfil del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con los datos del usuario
     */
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        User user = userService.findById(userId);
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * PUT /api/auth/profile
     * Actualiza el username, email y fecha de nacimiento del usuario autenticado.
     * Los errores de validación y de negocio son manejados por
     * {@link com.taskmaster.taskmasterbackend.exception.GlobalExceptionHandler}.
     *
     * @param request     nuevos datos del perfil validados con {@code @Valid}
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el usuario actualizado
     */
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        User user = userService.updateProfile(
                userId,
                request.getUsername(),
                request.getEmail(),
                request.getBirthDate()
        );
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * PATCH /api/auth/password
     * Cambia la contraseña del usuario autenticado tras verificar la actual.
     * Los errores son manejados por
     * {@link com.taskmaster.taskmasterbackend.exception.GlobalExceptionHandler}.
     *
     * @param request     contraseña actual y nueva contraseña validadas con {@code @Valid}
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK si el cambio fue exitoso
     */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        userService.changePassword(
                userId,
                request.getCurrentPassword(),
                request.getNewPassword()
        );
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/auth/account
     * Elimina permanentemente la cuenta del usuario autenticado y todos sus datos.
     * Requiere confirmación con la contraseña actual para evitar borrados accidentales.
     * Los errores son manejados por
     * {@link com.taskmaster.taskmasterbackend.exception.GlobalExceptionHandler}.
     *
     * @param password    contraseña actual para confirmar la operación
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content si la cuenta fue eliminada correctamente
     */
    @DeleteMapping(value = "/account")
    public ResponseEntity<Void> deleteAccount(
            @RequestParam String password,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        userService.deleteAccount(userId, password);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Convierte una entidad {@link User} al DTO de respuesta {@link UserResponse}.
     * La contraseña nunca se incluye en la respuesta.
     *
     * @param user entidad usuario a convertir
     * @return DTO con los datos del usuario listos para serializar a JSON
     */
    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                // hasAvatar evita exponer la ruta del fichero al cliente
                .hasAvatar(user.getAvatarPath() != null)
                .build();
    }
}
