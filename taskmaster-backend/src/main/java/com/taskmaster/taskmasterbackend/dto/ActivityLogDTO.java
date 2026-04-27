package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO que representa un registro de actividad para su transferencia al frontend.
 *
 * <p>Se usa para serializar los datos de {@link com.taskmaster.taskmasterbackend.model.ActivityLog}
 * sin exponer la entidad completa ni la referencia al usuario.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class ActivityLogDTO {

    /** Identificador único del registro. */
    private Long id;

    /** Tipo de acción realizada. */
    private ActionType actionType;

    /** Tipo de entidad afectada ({@code "TASK"}, {@code "PROJECT"}, etc.). */
    private String entityType;

    /** Identificador de la entidad afectada. */
    private Long entityId;

    /** Nombre de la entidad en el momento de la acción (snapshot). */
    private String entityName;

    /** Valor anterior del campo modificado. Usado en acciones de tipo {@code STATUS_CHANGED}. */
    private String oldValue;

    /** Nuevo valor del campo modificado. Usado en acciones de tipo {@code STATUS_CHANGED}. */
    private String newValue;

    /** Fecha y hora en que se registró la actividad. */
    private LocalDateTime createdAt;
}
