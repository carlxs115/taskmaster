package com.taskmaster.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ENTIDAD TASK
 *
 * Es la entidad central de TaskMaster.
 * Una tarea puede pertenecer a un proyecto y una tarea puede tener subtareas (que son otras Tasks).
 */
@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    /**
     * @Id                  → Es la clave primaria de la tabla
     * @GeneratedValue      → La BD genera el valor automáticamente (autoincremental)
     * GenerationType.IDENTITY → Delega la generación al motor de BD (H2, PostgreSQL...)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "El título de la tarea es obligatorio")
    private String title;

    @Column(length = 1000)
    private String description;

    /**
     * @Enumerated(EnumType.STRING)
     *
     * Le dice a JPA que guarde el enum como texto en la BD ("TODO", "IN_PROGRESS"...) y no como número (0, 1, 2...).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    // Fecha límite de la tarea (solo fecha, sin horas)
    private LocalDate dueDate;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * PROPIETARIO DE LA TAREA
     *
     * Siempre se rellena al crear la tarea, independientemente de si
     * tiene proyecto o no. Es la única forma de saber a quién pertenece
     * una tarea personal (project = null).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * RELACIÓN CON PROJECT (ManyToOne) - Opcional
     * Muchas tareas pertenecen a un proyecto.
     *
     * Si project es null -> es una tarea personal (sin proyecto).
     * Si project tiene valor -> pertenece a un proyecto.
     * nullable = true -> permite tareas sin proyecto en la BD.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = true)
    private Project project;

    /**
     * RELACIÓN CON SÍ MISMA — SUBTAREAS (ManyToOne)
     *
     * Una tarea puede tener una tarea padre (parentTask).
     * Si parentTask es null → es una tarea raíz (no es subtarea de nadie).
     * Si parentTask tiene valor → es subtarea de esa tarea.
     *
     * En la BD se crea una columna "parent_task_id" en la tabla "tasks" que apunta a otra fila de la misma tabla.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    /**
     * Lista de subtareas de esta tarea.
     * mappedBy = "parentTask" → la relación ya está definida arriba
     *
     * @ToString.Exclude → Le dice a Lombok que NO incluya este campo al generar
     *                     el toString(). Sin esto, al imprimir una Task,
     *                     Lombok intentaría imprimir sus subTasks, que a su vez
     *                     intentarían imprimir sus propias subTasks... entrando
     *                     en un bucle infinito (StackOverflowError).
     *
     * @EqualsAndHashCode.Exclude → Le dice a Lombok que NO incluya este campo al
     *                     generar equals() y hashCode(). Por la misma razón:
     *                     comparar una Task con sus subTasks que contienen
     *                     referencias a la Task padre causaría recursión infinita.
     */
    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> subTasks;

    /**
     * RELACIÓN CON TAG (ManyToMany)
     *
     * Una tarea puede tener múltiples etiquetas.
     * Una etiqueta puede estar en múltiples tareas.
     *
     * @JoinTable → JPA crea automáticamente una tabla intermedia llamada "task_tags"
     *              con dos columnas: task_id y tag_id
     *
     * @ToString.Exclude / @EqualsAndHashCode.Exclude → Mismo motivo que en subTasks.
     *                     Tag también tiene una lista de Tasks, por lo que sin estas
     *                     anotaciones Lombok generaría: Task → tags → Tag → tasks →
     *                     Task → tags... bucle infinito.
     */
    @ManyToMany
    @JoinTable(
            name = "task_tags",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Tag> tags;

    /**
     * @PrePersist → Se ejecuta automáticamente justo antes de guardar en la BD por primera vez
     * Así no tenemos que asignar la fecha manualmente nunca
     * Y si no se especifican los valores Estado u Prioridad se inicializan como:
     *      - Estado = Pendiente
     *      - Prioridad = Media
     */
    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();

        // Valores por defecto si no se especifican
        if (this.status == null) this.status = TaskStatus.TODO;
        if (this.priority == null) this.priority = TaskPriority.MEDIUM;
    }

    /**
     * SOFT DELETE — Papelera de reciclaje
     *
     * Mismo mecanismo que en Project.
     * Si deleted = true → la tarea está en la papelera.
     * Si deleted = false → la tarea está activa.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    /**
     * Categoría de la tarea.
     * Si la tarea pertenece a un proyecto, hereda su categoría automáticamente.
     * Si es una tarea sin proyecto, el usuario la asigna manualmente.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskCategory category;
}
