package com.taskmaster.taskmasterbackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta con los datos del perfil del usuario.
 *
 * <p>Se devuelve al frontend tras el login, registro o consulta del perfil.
 * La contraseña nunca se incluye en este DTO, ni siquiera cifrada.</p>
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

    /** Fecha de nacimiento del usuario. Se usa para mostrar el banner de cumpleaños. */
    private LocalDate birthDate;

    /** Fecha y hora de registro del usuario en el sistema. */
    private LocalDateTime createdAt;

    /**
     * Indica si el usuario tiene foto de perfil cargada.
     * El frontend usa este booleano para decidir si solicitar la imagen
     * al endpoint {@code GET /api/users/me/avatar}, evitando exponer
     * la ruta interna del fichero.
     */
    private boolean hasAvatar;
}