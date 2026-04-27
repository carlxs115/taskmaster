package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO para la solicitud de inicio de sesión.
 *
 * @author Carlos
 */
@Data
public class LoginRequest {

    /** Nombre de usuario. */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    /** Contraseña del usuario. */
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
