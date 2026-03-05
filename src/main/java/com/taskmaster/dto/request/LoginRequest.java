package com.taskmaster.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO DE LOGIN
 *
 * Solo necesita username y password.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
