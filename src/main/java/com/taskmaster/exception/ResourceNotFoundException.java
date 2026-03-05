package com.taskmaster.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * RESOURCENOTFOUNDEXCEPTION
 *
 * Se lanza cuando no se encuentra un recurso en la BD.
 * Por ejemplo: buscar una tarea con un id que no existe.
 *
 * @ResponseStatus → Cuando esta excepción llega al controlador,
 *                   Spring devuelve automáticamente un 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
