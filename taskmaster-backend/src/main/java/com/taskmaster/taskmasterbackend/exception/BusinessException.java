package com.taskmaster.taskmasterbackend.exception;

import java.io.Serial;

/**
 * Excepción para errores de reglas de negocio lanzados intencionalmente
 * por los servicios de TaskMaster.
 *
 * <p>Al ser una clase propia que hereda de RuntimeException, podemos
 * distinguirla de excepciones inesperadas de librerías externas (JPA,
 * Hibernate, etc.) y exponer su mensaje al cliente con total seguridad,
 * ya que siempre lo construimos nosotros en el código.</p>
 *
 * <p>Uso en un servicio:</p>
 * <pre>
 *   throw new BusinessException("No puedes completar una tarea con subtareas pendientes");
 * </pre>
 *
 * @author Carlos
 */
public class BusinessException extends RuntimeException{

    // Necesario para la serialización correcta de excepciones en Java
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea una nueva excepción de negocio con el mensaje descriptivo del error.
     * Este mensaje será visible para el cliente en la respuesta JSON.
     *
     * @param message descripción del error de negocio (p.ej. "La tarea ya está completada")
     */
    public BusinessException(String message) {
        super(message);
    }
}
