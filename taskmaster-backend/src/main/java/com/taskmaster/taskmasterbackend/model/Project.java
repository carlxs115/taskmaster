package com.taskmaster.taskmasterbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entidad que representa un proyecto dentro de la aplicación.
 *
 * <p>Un proyecto pertenece a un único usuario y puede contener múltiples tareas.
 * Soporta borrado lógico (soft delete) mediante el campo {@code deleted},
 * lo que permite enviar proyectos a la papelera antes de eliminarlos definitivamente.</p>
 *
 * <p><b>Nota de diseño:</b> los proyectos reutilizan los enums {@link TaskStatus},
 * {@link TaskPriority} y {@link TaskCategory} de las tareas porque comparten
 * los mismos valores posibles, evitando duplicar enums con el mismo contenido.</p>
 *
 * @author Carlos
 */
@Entity
@Table(
        name = "projects",

        // Índice en user_id porque todas las consultas de proyectos filtran por usuario
        indexes = @Index(name = "idx_projects_user_id", columnList = "user_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    /** Identificador único del proyecto, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre del proyecto. No puede estar vacío.
     * La validación real se realiza en el DTO de request con {@code @Valid}.
     */
    @Column(nullable = false)
    @NotBlank(message = "El nombre del proyecto es obligatorio")
    private String name;

    /**
     * Descripción opcional del proyecto.
     * Máximo 500 caracteres a nivel de base de datos.
     */
    @Column(length = 500)
    private String description;

    /**
     * Fecha y hora de creación del proyecto.
     * Se asigna automáticamente en {@link #onCreate()} y nunca puede modificarse
     * ({@code updatable = false}).
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Estado actual del proyecto.
     * Reutiliza {@link TaskStatus} porque los estados posibles son los mismos
     * que los de una tarea (TODO, IN_PROGRESS, DONE, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /**
     * Prioridad del proyecto (LOW, MEDIUM, HIGH, URGENT).
     * Reutiliza {@link TaskPriority} por la misma razón que {@code status}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    /**
     * Categoría del proyecto (PERSONAL, ESTUDIOS, TRABAJO).
     * Las tareas del proyecto heredan esta categoría al crearse.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskCategory category;

    /**
     * Usuario propietario del proyecto.
     * Carga lazy para no traer el usuario completo al consultar proyectos.
     * {@code @JsonIgnore} evita referencias circulares en la serialización JSON.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /**
     * Lista de tareas asociadas al proyecto.
     * {@code CascadeType.ALL} y {@code orphanRemoval = true} garantizan que
     * al eliminar el proyecto se eliminan también todas sus tareas.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> tasks;

    /**
     * Indica si el proyecto ha sido enviado a la papelera (soft delete).
     * Cuando es {@code true}, el proyecto no aparece en las listas normales,
     * pero sigue existiendo en la base de datos hasta su eliminación definitiva.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Fecha y hora en que el proyecto fue enviado a la papelera.
     * {@code TrashScheduler} la usa junto con {@code trashRetentionDays}
     * del usuario para calcular cuándo eliminarlo definitivamente.
     * Es {@code null} si el proyecto no está en la papelera.
     */
    private LocalDateTime deletedAt;

    /**
     * Asigna automáticamente la fecha y hora de creación antes de persistir
     * la entidad por primera vez.
     */
    @PrePersist
    protected void onCreate(){

        // Se asigna en @PrePersist para garantizar que refleja el momento
        // real de escritura en base de datos
        this.createdAt = LocalDateTime.now();
    }
}
