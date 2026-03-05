package com.taskmaster.controller;

import com.taskmaster.dto.request.LoginRequest;
import com.taskmaster.dto.request.RegisterRequest;
import com.taskmaster.dto.response.UserResponse;
import com.taskmaster.model.User;
import com.taskmaster.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        // Convertimos la entidad User al DTO de respuesta
        // Nunca devolvemos la entidad directamente - podría exponer la contraseña
        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthDate(user.getBirthDate())
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

            UserResponse response = UserResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .birthDate(user.getBirthDate())
                    .createdAt(user.getCreatedAt())
                    .build();

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            // Credenciales incorrectas = 401 Unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Credenciales incorrectas");
        }
    }
}
