package com.taskmaster.taskmasterbackend.model;

import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad que representa un registro de actividad en el sistema.
 *
 * <p>Cada vez que un usuario realiza una acción relevante (crear, editar, eliminar
 * una tarea, proyecto, etc.), se genera un {@code ActivityLog} que queda
 * almacenado para su consulta en el historial de actividad.</p>
 *
 * @author Carlos
 */
@Entity
@Table(name = "activity_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {

    /** Identificador único del registro. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Usuario que realizó la acción. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Tipo de acción realizada (CREATE, UPDATE, DELETE, STATUS_CHANGED, etc.). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    /** Tipo de entidad afectada: {@code "TASK"}, {@code "SUBTASK"}, {@code "PROJECT"} o {@code "PROFILE"}. */
    @Column
    private String entityType;

    /** Identificador de la entidad afectada. Puede ser {@code null} si la entidad ya no existe. */
    @Column
    private Long entityId;

    /**
     * Nombre de la entidad en el momento de la acción (snapshot).
     * Se conserva aunque la entidad sea eliminada posteriormente,
     * para que el historial siga siendo legible.
     */
    @Column
    private String entityName;

    /** Valor anterior del campo modificado. Usado principalmente en acciones de tipo {@code STATUS_CHANGED}. */
    @Column
    private String oldValue;

    /** Nuevo valor del campo modificado. Usado principalmente en acciones de tipo {@code STATUS_CHANGED}. */
    @Column
    private String newValue;

    /** Fecha y hora en que se registró la actividad. Se asigna automáticamente al persistir. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Asigna automáticamente la fecha y hora de creación antes de persistir la entidad.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
