package com.taskmaster.taskmasterbackend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
 *   <li>{@link HttpMessageNotReadableException} y {@link MethodArgumentTypeMismatchException}
 *       - cuerpo JSON mal formado o parámetro con tipo incorrecto (400)</li>
 *   <li>{@link RuntimeException} - fallos internos inesperados (500)</li>
 *   <li>{@link Exception} - red de seguridad final para cualquier otro error (500)</li>
 * </ol>
 *
 * <p>Las excepciones de Spring MVC que implementan {@link ErrorResponse} (parámetro
 * obligatorio ausente, ruta inexistente, método no permitido, fichero demasiado grande…)
 * conservan su propio código HTTP, aunque lleguen a los dos últimos manejadores.</p>
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
     * Captura peticiones mal formadas que Spring detecta como {@link RuntimeException}:
     * un cuerpo JSON que no se puede leer o un parámetro con un tipo incorrecto
     * (p.ej. {@code ?status=FOO} o {@code /api/tasks/abc}).
     *
     * <p>Son errores del cliente, así que no deben caer en el 500 de
     * {@link #handleRuntimeException}. No se expone el mensaje original.</p>
     *
     * @param ex excepción de lectura del cuerpo o de conversión de tipo
     * @return 400 Bad Request con mensaje genérico
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {

        log.warn("Petición no válida ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());

        Map<String, Object> response = buildResponse(HttpStatus.BAD_REQUEST, "Petición no válida");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Captura {@link RuntimeException} inesperadas procedentes de librerías externas
     * como JPA o Hibernate: son fallos del servidor, no del cliente.
     *
     * <p>Si la excepción es de Spring MVC e implementa {@link ErrorResponse}
     * (p.ej. fichero demasiado grande), se respeta su código HTTP.</p>
     *
     * <p><b>Nota de seguridad:</b> nunca se expone {@code ex.getMessage()} al cliente porque
     * puede contener nombres de tablas SQL, rutas internas u otra información sensible.
     * El error real se loguea en el servidor para diagnóstico.</p>
     *
     * @param ex excepción de runtime inesperada
     * @return 500 Internal Server Error con mensaje genérico, o el código de la {@link ErrorResponse}
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {

        if (ex instanceof ErrorResponse errorResponse) {
            return handleFrameworkError(errorResponse, ex);
        }

        // Logueamos el error completo (con stack trace) en el servidor para poder diagnosticarlo,
        // pero devolvemos un mensaje genérico al cliente para no filtrar información interna
        log.error("RuntimeException inesperada: {}", ex.getMessage(), ex);

        Map<String, Object> response = buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Red de seguridad final: captura cualquier excepción no controlada por los manejadores anteriores.
     *
     * <p>Las excepciones de Spring MVC que implementan {@link ErrorResponse}
     * (p.ej. falta un {@code @RequestParam} obligatorio, ruta inexistente o método
     * no permitido) son errores del cliente y conservan su código HTTP.</p>
     *
     * <p><b>Seguridad:</b> igual que en {@link #handleRuntimeException}, nunca
     * se expone el mensaje original al cliente.</p>
     *
     * @param ex excepción inesperada no controlada
     * @return 500 Internal Server Error con mensaje genérico, o el código de la {@link ErrorResponse}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        if (ex instanceof ErrorResponse errorResponse) {
            return handleFrameworkError(errorResponse, ex);
        }

        // Logueamos internamente con stack trace completo para diagnóstico
        log.error("Excepción no controlada: {}", ex.getMessage(), ex);

        Map<String, Object> response = buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // -------------------------------------------------------------------------
    // Utilidades internas
    // -------------------------------------------------------------------------

    /**
     * Responde a una excepción de Spring MVC con el código HTTP que ella misma declara
     * ({@link ErrorResponse#getStatusCode()}) y un mensaje genérico según ese código.
     *
     * @param errorResponse la excepción vista como {@link ErrorResponse}
     * @param ex            la misma excepción, para el log
     * @return respuesta con el código de la excepción y mensaje genérico
     */
    private ResponseEntity<Map<String, Object>> handleFrameworkError(ErrorResponse errorResponse, Exception ex) {
        HttpStatusCode statusCode = errorResponse.getStatusCode();

        String message;
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            message = "Recurso no encontrado";
        } else if (statusCode.is4xxClientError()) {
            message = "Petición no válida";
        } else {
            message = "Error interno del servidor";
        }

        if (statusCode.is5xxServerError()) {
            log.error("Error de Spring MVC ({}): {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        } else {
            log.warn("Petición rechazada ({} → {}): {}", ex.getClass().getSimpleName(), statusCode.value(), ex.getMessage());
        }

        Map<String, Object> response = buildResponse(statusCode, message);
        return ResponseEntity.status(statusCode).body(response);
    }

    /**
     * Construye la estructura base común a todas las respuestas de error:
     * timestamp, código HTTP numérico y mensaje descriptivo.
     *
     * @param status  código HTTP de la respuesta
     * @param message mensaje de error a incluir en el JSON
     * @return mapa con los campos base listos para serializar a JSON
     */
    private Map<String, Object> buildResponse(HttpStatusCode status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());   // momento exacto del error
        response.put("status", status.value());           // código numérico: 400, 404, 500…
        response.put("error", message);
        return response;
    }
}