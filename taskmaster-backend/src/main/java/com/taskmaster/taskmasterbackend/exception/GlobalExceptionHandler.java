package com.taskmaster.taskmasterbackend.exception;

import lombok.extern.slf4j.Slf4j;
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
 * <p>Orden de prioridad de los manejadores (Spring elige el más específico):</p>
 * <ol>
 *   <li>{@link MethodArgumentNotValidException} - errores de validación de campos</li>
 *   <li>{@link ResourceNotFoundException} - recurso no encontrado en BD</li>
 *   <li>{@link BusinessException} - reglas de negocio violadas intencionalmente</li>
 *   <li>{@link RuntimeException} - excepciones inesperadas de librerías externas</li>
 *   <li>{@link Exception} - red de seguridad final para cualquier otro error</li>
 * </ol>
 *
 * @author Carlos
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // Manejadores de excepciones
    // -------------------------------------------------------------------------

    /**
     * Captura errores de validación producidos cuando falla {@code @Valid}.
     * Devuelve un mapa con los mensajes de error agrupados por campo,
     * de forma que el frontend sepa exactamente qué campo resaltar.
     *
     * <p>Ejemplo de respuesta:</p>
     * <pre>
     * {
     *   "status": 400,
     *   "error": "Error de validación",
     *   "fields": {
     *     "title":   "El título no puede estar vacío",
     *     "dueDate": "Formato de fecha inválido"
     *   }
     * }
     * </pre>
     *
     * @param ex excepción de validación lanzada automáticamente por Spring
     * @return 400 Bad Request con los errores detallados por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {

        // Recorremos todos los errores de campo y los agrupamos en un mapa:
        //   clave = nombre del campo que falló (p.ej. "title")
        //   valor = mensaje de la anotación @NotBlank, @NotNull, etc. (p.ej. "No puede estar vacío")
        // Así el frontend puede resaltar exactamente el campo problemático
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        // Construimos la respuesta base y le añadimos el detalle de campos
        Map<String, Object> response = buildResponse(HttpStatus.BAD_REQUEST, "Error de validación");
        response.put("fields", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Captura excepciones {@link ResourceNotFoundException}, lanzadas cuando
     * no se encuentra una entidad en la base de datos (p.ej. tarea o proyecto inexistente).
     *
     * @param ex excepción con el mensaje descriptivo del recurso no encontrado
     * @return 404 Not Found con el mensaje de la excepción
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {

        // El mensaje de ResourceNotFoundException es seguro: lo construimos nosotros
        // en los servicios, por ejemplo: "Tarea no encontrada con id: 5"
        Map<String, Object> response = buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Captura excepciones {@link BusinessException}, lanzadas intencionalmente
     * por los servicios cuando se viola una regla de negocio.
     *
     * <p>El mensaje se expone al cliente porque siempre lo construimos nosotros,
     * por ejemplo: "No puedes completar una tarea con subtareas pendientes".</p>
     *
     * @param ex excepción de negocio con mensaje controlado
     * @return 400 Bad Request con el mensaje descriptivo del error
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {

        // Mensaje seguro: lo hemos construido nosotros en el servicio correspondiente
        Map<String, Object> response = buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Captura {@link RuntimeException} inesperadas procedentes de librerías externas
     * como JPA o Hibernate. REEMPLAZA al anterior manejador genérico de RuntimeException.
     *
     * <p><b>Nota de seguridad:</b> nunca se expone {@code ex.getMessage()} al cliente porque
     * puede contener nombres de tablas SQL, rutas internas u otra información sensible.
     * El error real se loguea en el servidor para diagnóstico.</p>
     *
     * @param ex excepción de runtime inesperada
     * @return 400 Bad Request con mensaje genérico
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {

        // Logueamos el error completo (con stack trace) en el servidor para poder diagnosticarlo,
        // pero devolvemos un mensaje genérico al cliente para no filtrar información interna
        log.error("RuntimeException inesperada: {}", ex.getMessage(), ex);

        Map<String, Object> response = buildResponse(HttpStatus.BAD_REQUEST, "Error inesperado en la operación");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Red de seguridad final: captura cualquier excepción no controlada por los manejadores anteriores.
     *
     * <p><b>Seguridad:</b> igual que en {@link #handleRuntimeException}, nunca
     * se expone el mensaje original al cliente.</p>
     *
     * @param ex excepción inesperada no controlada
     * @return 500 Internal Server Error con mensaje genérico
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        // Logueamos internamente con stack trace completo para diagnóstico
        log.error("Excepción no controlada: {}", ex.getMessage(), ex);

        Map<String, Object> response = buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // -------------------------------------------------------------------------
    // Utilidades internas
    // -------------------------------------------------------------------------

    /**
     * Construye la estructura base común a todas las respuestas de error:
     * timestamp, código HTTP numérico y mensaje descriptivo.
     *
     * @param status  código HTTP de la respuesta
     * @param message mensaje de error a incluir en el JSON
     * @return mapa con los campos base listos para serializar a JSON
     */
    private Map<String, Object> buildResponse(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());   // momento exacto del error
        response.put("status", status.value());           // código numérico: 400, 404, 500…
        response.put("error", message);
        return response;
    }
}