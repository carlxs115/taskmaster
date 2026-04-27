package com.taskmaster.taskmasterbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción que se lanza cuando no se encuentra un recurso solicitado en la base de datos.
 *
 * <p>Al estar anotada con {@code @ResponseStatus(HttpStatus.NOT_FOUND)}, Spring devuelve
 * automáticamente un 404 cuando esta excepción llega a un controlador. Además,
 * es capturada por {@link GlobalExceptionHandler} para devolver una respuesta
 * JSON estructurada.</p>
 *
 * @author Carlos
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Crea una nueva excepción con el mensaje descriptivo del recurso no encontrado.
     *
     * @param message descripción del recurso que no se ha encontrado
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
