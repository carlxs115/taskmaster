package com.taskmaster.taskmasterbackend.model;

import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entidad que representa un registro de trabajo asociado a una tarea.
 *
 * <p>Permite al usuario registrar el tiempo dedicado a una tarea,
 * especificando la fecha, el tipo de actividad, las horas invertidas
 * y una nota opcional descriptiva.</p>
 *
 * @author Carlos
 */
@Entity
@Table(name = "work_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkLog {

    /** Identificador único del registro, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fecha en que se realizó el trabajo registrado. */
    @Column(nullable = false)
    private LocalDate date;

    /** Tipo de actividad realizada (desarrollo, diseño, reunión, etc.). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;

    /** Número de horas dedicadas. Admite hasta 6 dígitos con 2 decimales. */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal hours;

    /** Nota opcional con detalles adicionales sobre el trabajo realizado. */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** Tarea a la que pertenece este registro de trabajo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Task task;
}
