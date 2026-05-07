package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO para la solicitud de inicio de sesión.
 *
 * <p>El campo {@code username} acepta tanto el nombre de usuario como
 * el email, ya que {@code UserDetailsServiceImpl} intenta la búsqueda
 * por ambos campos.</p>
 *
 * @author Carlos
 */
@Data
public class LoginRequest {

    /**
     * Nombre de usuario o email del usuario.
     * Se acepta cualquiera de los dos como identificador de autenticación.
     */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    /** Contraseña del usuario. */
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
