package com.taskmaster.taskmasterbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * DTO de respuesta tras preparar la restauración de una copia de seguridad.
 *
 * <p>Devuelve los usuarios que contiene la copia para que el frontend indique con
 * qué cuenta hay que iniciar sesión después de reiniciar.</p>
 *
 * @author Carlos
 */
@Data
@AllArgsConstructor
public class RestoreStagedResponse {

    /** Nombres de usuario de la copia, en orden alfabético. */
    private List<String> usernames;
}
