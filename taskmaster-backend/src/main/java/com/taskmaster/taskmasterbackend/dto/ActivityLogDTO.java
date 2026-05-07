package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO que representa un registro de actividad para su transferencia al frontend.
 *
 * <p>Se usa para serializar los datos de
 * {@link com.taskmaster.taskmasterbackend.model.ActivityLog}
 * sin exponer la entidad completa ni la referencia al usuario.</p>
 *
 * <p><b>Nota técnica:</b> se mantiene como clase con {@code @Builder} en lugar
 * de {@code record} porque el patrón builder facilita la construcción en
 * {@code ActivityLogController.toDTO} con campos opcionales como
 * {@code oldValue} y {@code newValue}.</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class ActivityLogDTO {

    /** Identificador único del registro. */
    private Long id;

    /** Tipo de acción realizada (CREATE, UPDATE, DELETE, STATUS_CHANGED, etc.). */
    private ActionType actionType;

    /**
     * Tipo de entidad afectada.
     * Valores posibles: {@code "TASK"}, {@code "SUBTASK"}, {@code "PROJECT"},
     * {@code "PROFILE"}, o {@code null} para acciones sin entidad (login, logout).
     */
    private String entityType;

    /**
     * Identificador de la entidad afectada.
     * Puede ser {@code null} si la acción no está asociada a una entidad concreta.
     */
    private Long entityId;

    /**
     * Nombre de la entidad en el momento de la acción (snapshot).
     * Se conserva aunque la entidad sea eliminada posteriormente.
     */
    private String entityName;

    /**
     * Valor anterior del campo modificado.
     * Usado principalmente en acciones {@code STATUS_CHANGED} y {@code TASK_EDITED}.
     * {@code null} si la acción no implica cambio de valor.
     */
    private String oldValue;

    /**
     * Nuevo valor del campo modificado tras la acción.
     * {@code null} si la acción no implica cambio de valor.
     */
    private String newValue;

    /** Fecha y hora exacta en que se registró la actividad. */
    private LocalDateTime createdAt;
}
