package com.taskmaster.taskmasterbackend.dto.transfer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.taskmaster.taskmasterbackend.model.enums.ActivityType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Registro de trabajo de una tarea dentro del fichero de exportación e importación de datos.
 *
 * @param date         fecha en que se realizó el trabajo
 * @param activityType tipo de actividad
 * @param hours        horas dedicadas (entre 0,1 y 24)
 * @param note         nota opcional, o {@code null}
 * @author Carlos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkLogData(
        LocalDate date,
        ActivityType activityType,
        BigDecimal hours,
        String note
) {
}
