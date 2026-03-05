package com.taskmaster.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO DE RESPUESTA DE USUARIO
 *
 * Lo que el backend devuelve al frontend tras login o registro.
 * NUNCA incluimos la contraseña, aunque esté cifrada.
 */
@Data
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private LocalDate birthDate;
    private LocalDateTime createdAt;
}
