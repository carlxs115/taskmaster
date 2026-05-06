package com.taskmaster.taskmasterbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * Excepción lanzada cuando no se encuentra un recurso solicitado en la base de datos.
 *
 * <p>Al estar anotada con {@code @ResponseStatus(HttpStatus.NOT_FOUND)}, Spring devuelve
 * automáticamente un 404 si la excepción llega a un controlador sin ser interceptada.
 * En la práctica, es capturada por {@link GlobalExceptionHandler} para devolver
 * una respuesta JSON con estructura consistente.</p>
 *
 * <p>Uso típico en un servicio:</p>
 * <pre>
 *     throw new ResourceNotFoundException("Tarea no encontrada con id: " + id);
 * </pre>
 *
 * @author Carlos
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    // Necesario para la serialización correcta de excepciones en Java
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea una nueva excepción con el mensaje descriptivo del recurso no encontrado.
     *
     * @param message descripción del recurso que no existe (p.ej. "Proyecto no encontrado con id: 5")
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
