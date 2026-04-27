package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO para la solicitud de cambio de contraseña.
 *
 * @author Carlos
 */
@Data
public class ChangePasswordRequest {

    /** Contraseña actual del usuario, necesaria para verificar la identidad. */
    @NotBlank(message = "La contraseña es obligatoria")
    private String currentPassword;

    /** Nueva contraseña. Debe tener al menos 6 caracteres. */
    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
    private String newPassword;
}
