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
 * AUTHCONTROLLER
 *
 * Gestiona el registro y login de usuarios.
 * Estos endpoints son públicos - no requieren autenticación previa
 * (configurado en SecurityConfig con .permitAll())
 *
 * @RestController -> Combina @Controller y @ResponseBody.
 *                   Indica que esta clase maneja peticiones HTTP
 *                   y devuelve JSON automáticamente.
 *
 * @RequestMapping -> Prefijo de todas las rutas de este controlador.
 *                   Todas las rutas empezarán por /api/auth/
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;

    /**
     * REGISTRO - POST /api/auth/register
     *
     * Recibe los datos del formulario de registro y crea un nuevo usuario.
     *
     * @Valid -> Activa las validaciones del DTO (@NotBlank, @Email, @NotNull...)
     *          Si alguna falla, Spring devuelve automáticamente un 400 Bad Request.
     *
     * @RequestBody -> Le dice a Spring que el cuerpo de la petición HTTP
     *                es un JSON y lo convierte al DTO automáticamente.
     *
     * ResponseEntity -> Permite controlar el código HTTP de la respuesta.
     *                  201 Created es el código correcto para creación de recursos.
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
     * LOGIN — POST /api/auth/login
     *
     * Autentica al usuario con username y password.
     * Si las credenciales son correctas devuelve los datos del usuario.
     * Si son incorrectas devuelve 401 Unauthorized.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            /**
             * authenticationManager.authenticate() hace todo el trabajo:
             *      1. Llama a UserDetailsServiceImpl.loadUserByUsername()
             *      2. Compara la contraseña introducida con la cifrada en BD
             *      3. Si son correctas devuelve un objeto Authentication
             *      4. Si no, lanza AuthenticationException
             */
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Cargamos el usuario completo para devolver sus datos
            User user = userService.findByUsername(request.getUsername());
            activityLogService.log(user.getId(), ActionType.LOGIN);
            return ResponseEntity.ok(toResponse(user));

        } catch (AuthenticationException e) {
            // Credenciales incorrectas = 401 Unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Credenciales incorrectas");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        activityLogService.log(userId, ActionType.LOGOUT);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/auth/profile
     * Devuelve los datos del usuario autenticado.
     */
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        User user = userService.findById(userId);

        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * PUT /api/auth/profile
     * Actualiza username, email y fecha de nacimiento.
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            Long userId = securityUtils.getUserId(userDetails);
            User user = userService.updateProfile(
                    userId,
                    request.getUsername(),
                    request.getEmail(),
                    request.getBirthDate()
            );

            return ResponseEntity.ok(toResponse(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PATCH /api/auth/password
     * Cambia la contraseña del usuario autenticado.
     */
    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            Long userId = securityUtils.getUserId(userDetails);
            userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/auth/account
     * Elimina permanentemente la cuenta del usuario autenticado.
     * Requiere confirmación con contraseña.
     */
    @RequestMapping(value = "/account", method = RequestMethod.DELETE)
    public ResponseEntity<?> deleteAccount(
            @RequestParam String password,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long userId = securityUtils.getUserId(userDetails);
            userService.deleteAccount(userId, password);

            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                .hasAvatar(user.getAvatarPath() != null)
                .build();
    }
}
