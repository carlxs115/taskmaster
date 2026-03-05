package com.taskmaster.model;

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
}
