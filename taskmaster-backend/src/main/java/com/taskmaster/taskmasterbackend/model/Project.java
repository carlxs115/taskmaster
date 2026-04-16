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
 * ENTIDAD PROJECT
 *
 * Un proyecto pertenece a un usuario y contiene múltiples tareas.
 */
@Entity
@Table(name = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    /**
     * @Id                  → Es la clave primaria de la tabla
     * @GeneratedValue      → La BD genera el valor automáticamente (autoincremental)
     * GenerationType.IDENTITY → Delega la generación al motor de BD (H2, PostgreSQL...)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "El nombre del proyecto es obligatorio")
    private String name;

    @Column(length = 500)
    private String description;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    /**
     * RELACIÓN CON USER (ManyToOne)
     *
     * Muchos proyectos pueden pertenecer a un mismo usuario.
     *
     * @ManyToOne  → Muchos proyectos → Un usuario
     * @JoinColumn → Define la columna de la clave foránea en la tabla "projects"
     *               En la BD se creará una columna llamada "user_id"
     *               que apunta al id de la tabla "users"
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * RELACIÓN CON TASK (OneToMany)
     *
     * Un proyecto puede tener múltiples tareas.
     * Si se borra el proyecto, se borran todas sus tareas (cascade).
     */
    @JsonIgnore
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> tasks;

    /**
     * @PrePersist → Se ejecuta automáticamente justo antes de guardar en la BD por primera vez
     * Así no tenemos que asignar la fecha manualmente nunca.
     */
    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    /**
     * SOFT DELETE — Papelera de reciclaje
     *
     * En vez de borrar físicamente el proyecto de la BD,
     * lo marcamos como eliminado con deleted = true.
     * deletedAt guarda cuándo fue enviado a la papelera,
     * para calcular cuándo debe borrarse definitivamente
     * según las preferencias del usuario.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    /**
     * Categoría del proyecto.
     * Todas las tareas y subtareas de este proyecto heredarán esta categoría.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskCategory category;
}
