package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO para la solicitud de registro de un nuevo usuario.
 *
 * <p>Recibe los datos del frontend sin exponer campos internos de la entidad
 * como {@code id} o {@code createdAt}, permitiendo validarlos antes de procesarlos.</p>
 *
 * @author Carlos
 */
@Data
public class RegisterRequest {

    /** Nombre de usuario elegido. */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    /** Correo electrónico del usuario. */
    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    /** Contraseña del usuario. */
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    /** Fecha de nacimiento del usuario. */
    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate birthDate;
}
