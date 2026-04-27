package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO que representa un registro de trabajo para su creación y transferencia.
 *
 * <p>Se usa tanto para recibir datos del frontend al crear o editar un registro
 * como para devolverlos en las respuestas de la API.</p>
 *
 * @param id           identificador único del registro, {@code null} en creación
 * @param date         fecha en que se realizó el trabajo
 * @param activityType tipo de actividad realizada
 * @param hours        horas dedicadas, debe ser mayor o igual a 0.1
 * @param note         nota opcional con detalles adicionales
 *
 * @author Carlos
 */
public record WorkLogDTO (
        Long id,
        @NotNull LocalDate date,
        @NotNull ActivityType activityType,
        @NotNull @DecimalMin("0.1") BigDecimal hours,
        String note
) {}
