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
 * @author Carlos
 */
@Entity
@Table(name = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    /** Identificador único del proyecto, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre del proyecto. No puede estar vacío. */
    @Column(nullable = false)
    @NotBlank(message = "El nombre del proyecto es obligatorio")
    private String name;

    /** Descripción opcional del proyecto. Máximo 500 caracteres. */
    @Column(length = 500)
    private String description;

    /** Fecha y hora de creación. Se asigna automáticamente al persistir y no puede modificarse. */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Estado actual del proyecto (TODO, IN_PROGRESS, DONE, etc.). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /** Prioridad del proyecto (LOW, MEDIUM, HIGH, URGENT). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    /** Categoría del proyecto (PERSONAL, ESTUDIOS, TRABAJO). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskCategory category;

    /**
     * Usuario propietario del proyecto.
     * Muchos proyectos pueden pertenecer a un mismo usuario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Lista de tareas asociadas al proyecto.
     * Si el proyecto es eliminado, todas sus tareas se eliminan en cascada.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> tasks;

    /**
     * Indica si el proyecto ha sido enviado a la papelera.
     * Cuando es {@code true}, el proyecto no aparece en las listas normales.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Fecha y hora en que el proyecto fue enviado a la papelera.
     * Se usa junto con las preferencias del usuario para calcular
     * cuándo debe eliminarse definitivamente.
     */
    private LocalDateTime deletedAt;

    /**
     * Asigna automáticamente la fecha y hora de creación antes de persistir la entidad.
     */
    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }



}
