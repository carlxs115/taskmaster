package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO DE REGISTRO
 *
 * Contiene los datos que el frontend envía para registrar un usuario.
 * Usamos un DTO en vez de la entidad directamente por dos razones:
 *      1. Seguridad - no exponemos campos internos como id o createdAt
 *      2. Flexibilidad - podemos validar los datos antes de procesarlos
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate birthDate;
}
