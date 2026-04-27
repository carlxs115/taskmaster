package com.taskmaster.taskmasterbackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta con los datos del usuario.
 *
 * <p>Se devuelve al frontend tras el login o registro.
 * No incluye la contraseña bajo ninguna circunstancia, ni siquiera cifrada.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class UserResponse {

    /** Identificador único del usuario. */
    private Long id;

    /** Nombre de usuario. */
    private String username;

    /** Correo electrónico del usuario. */
    private String email;

    /** Fecha de nacimiento del usuario. */
    private LocalDate birthDate;

    /** Fecha y hora de registro del usuario. */
    private LocalDateTime createdAt;

    /** Indica si el usuario tiene foto de perfil cargada. */
    private boolean hasAvatar;
}
