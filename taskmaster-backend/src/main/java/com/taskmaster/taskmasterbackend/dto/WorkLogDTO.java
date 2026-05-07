package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO que representa un registro de trabajo para su creación y transferencia.
 *
 * <p>Se usa tanto para recibir datos del frontend al crear o editar un registro
 * como para devolverlos en las respuestas de la API. Al ser un {@code record},
 * es inmutable y las validaciones se aplican automáticamente con {@code @Valid}
 * en el controlador.</p>
 *
 * @param id           identificador único del registro, {@code null} en creación
 * @param date         fecha en que se realizó el trabajo
 * @param activityType tipo de actividad realizada
 * @param hours        horas dedicadas, entre 0.1 y 24.0
 * @param note         nota opcional con detalles adicionales
 *
 * @author Carlos
 */
public record WorkLogDTO (
        Long id,

        @NotNull(message = "La fecha es obligatoria")
        LocalDate date,

        @NotNull(message = "El tipo de actividad es obligatorio")
        ActivityType activityType,

        @NotNull(message = "Las horas son obligatorias")
        @DecimalMin(value = "0.1", message = "Las horas deben ser al menos 0.1")
        @DecimalMax(value = "24.0", message = "Las horas no pueden superar 24 por registro")
        BigDecimal hours,

        String note
) {}