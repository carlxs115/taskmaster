package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para la solicitud de actualización del perfil de usuario.
 *
 * @author Carlos
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    /** Nuevo nombre de usuario. */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    /** Nuevo correo electrónico. */
    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    /** Nueva fecha de nacimiento. */
    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate birthDate;
}
