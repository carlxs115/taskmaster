package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO para la solicitud de registro de un nuevo usuario.
 *
 * <p>Recibe los datos del frontend sin exponer campos internos de la entidad
 * como {@code id} o {@code createdAt}. Las validaciones se aplican
 * automáticamente gracias a {@code @Valid} en el controlador.</p>
 *
 * @author Carlos
 */
@Data
public class RegisterRequest {

    /**
     * Nombre de usuario elegido.
     * Debe ser único en el sistema (validado en {@code UserService.register}).
     */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    /**
     * Correo electrónico del usuario.
     * Debe ser único y tener formato válido.
     */
    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    /**
     * Contraseña del usuario en texto plano.
     * Se cifrará con BCrypt en {@code UserService.register} antes de persistirse.
     * Nunca se almacena ni se devuelve en texto plano.
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    /** Fecha de nacimiento del usuario. Se usa para mostrar el banner de cumpleaños. */
    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate birthDate;
}
