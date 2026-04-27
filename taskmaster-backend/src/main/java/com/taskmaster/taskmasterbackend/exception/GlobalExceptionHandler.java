package com.taskmaster.taskmasterbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones de la aplicación.
 *
 * <p>Intercepta todas las excepciones lanzadas en cualquier controlador
 * y las convierte en respuestas JSON estructuradas y consistentes,
 * evitando que el frontend reciba errores genéricos o sin formato.</p>
 *
 * @author Carlos
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura errores de validación producidos cuando falla {@code @Valid}.
     * Devuelve un mapa con los mensajes de error agrupados por campo.
     *
     * @param exception excepción de validación lanzada por Spring
     * @return 400 Bad Request con los errores de cada campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException exception) {

        Map<String, String> fieldErrors = new HashMap<>();

        // Recorremos todos los errores de validación y los agrupamos por campo
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 400);
        response.put("error", "Error de validación");
        response.put("fields", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Captura excepciones de tipo {@link ResourceNotFoundException}.
     *
     * @param exception excepción lanzada cuando no se encuentra un recurso
     * @return 404 Not Found con el mensaje del error
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException exception) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 404);
        response.put("error", exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Captura excepciones de tipo {@link RuntimeException} no controladas específicamente.
     * Por ejemplo: reglas de negocio como intentar completar una tarea con subtareas pendientes.
     *
     * @param exception excepción de tiempo de ejecución
     * @return 400 Bad Request con el mensaje del error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRunTimeException(
            RuntimeException exception) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 400);
        response.put("error", exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Captura cualquier excepción inesperada no controlada por los demás manejadores.
     * Actúa como red de seguridad final para evitar que el servidor exponga información interna.
     *
     * @param exception excepción genérica no controlada
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception exception) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", 500);
        response.put("error", "Error interno del servidor");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
