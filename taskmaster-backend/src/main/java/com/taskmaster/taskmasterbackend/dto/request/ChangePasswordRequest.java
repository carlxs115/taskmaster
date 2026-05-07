package com.taskmaster.taskmasterbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO para la solicitud de cambio de contraseña.
 *
 * <p>Requiere la contraseña actual para verificar la identidad del usuario
 * antes de permitir el cambio, evitando que alguien con la sesión abierta
 * pueda cambiar la contraseña sin conocer la actual.</p>
 *
 * @author Carlos
 */
@Data
public class ChangePasswordRequest {

    /**
     * Contraseña actual del usuario.
     * Se usa para verificar la identidad antes de aplicar el cambio.
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña actual debe tener al menos 6 caracteres")
    private String currentPassword;

    /**
     * Nueva contraseña a establecer.
     * Debe tener al menos 6 caracteres.
     */
    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
    private String newPassword;
}
