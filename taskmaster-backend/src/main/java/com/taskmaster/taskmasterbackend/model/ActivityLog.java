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
@Table(
        name = "activity_log",
        // Índice en user_id porque todas las consultas filtran por usuario.
        // Sin índice, con muchos registros sería un full table scan en cada carga
        // del historial de actividad.
        indexes = @Index(name = "idx_activity_log_user_id", columnList = "user_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {

    /** Identificador único del registro, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Usuario que realizó la acción.
     * Carga lazy para no traer el usuario completo en cada consulta del log.
     */
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

    /**
     * Identificador de la entidad afectada.
     * Puede ser {@code null} si la entidad fue eliminada posteriormente,
     * por eso no se usa una FK real sino un Long simple.
     */
    @Column
    private Long entityId;

    /**
     * Nombre de la entidad en el momento de la acción (snapshot).
     * Se conserva aunque la entidad sea eliminada posteriormente,
     * para que el historial siga siendo legible con contexto.
     */
    @Column
    private String entityName;

    /** Valor anterior del campo modificado. Usado principalmente en acciones de tipo {@code STATUS_CHANGED}. */
    @Column
    private String oldValue;

    /** Nuevo valor del campo modificado. Usado principalmente en acciones de tipo {@code STATUS_CHANGED}. */
    @Column
    private String newValue;

    /**
     * Fecha y hora en que se registró la actividad.
     * Se asigna automáticamente en {@link #onCreate()}.
     *
     * <p><b>Nota:</b> usa {@code LocalDateTime} sin zona horaria,
     * lo que es suficiente para una app monousuario de escritorio.
     * Si la app se ejecutara en múltiples zonas horarias habría
     * que migrar a {@code Instant} o {@code ZonedDateTime}.</p>
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Asigna automáticamente la fecha y hora actuales antes de persistir
     * el registro por primera vez. JPA llama a este método automáticamente
     * gracias a {@code @PrePersist}.
     */
    @PrePersist
    protected void onCreate() {

        // Se asigna aquí y no en el constructor para garantizar que siempre
        // refleja el momento real de persistencia, independientemente de
        // cuándo se construyó el objeto en memoria.
        createdAt = LocalDateTime.now();
    }
}
