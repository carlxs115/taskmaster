package com.taskmaster.taskmasterbackend.model;

import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad central de TaskMaster que representa una tarea.
 *
 * <p>Una tarea puede ser independiente (personal) o pertenecer a un proyecto.
 * También puede actuar como tarea padre conteniendo subtareas, que son a su vez
 * instancias de {@code Task} enlazadas mediante una relación recursiva.</p>
 *
 * <p>Soporta borrado lógico (soft delete) mediante el campo {@code deleted},
 * lo que permite enviar tareas a la papelera antes de eliminarlas definitivamente.</p>
 *
 * @author Carlos
 */
@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    /** Identificador único de la tarea, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Título de la tarea. No puede estar vacío. */
    @Column(nullable = false)
    @NotBlank(message = "El título de la tarea es obligatorio")
    private String title;

    /** Descripción opcional de la tarea. Máximo 1000 caracteres. */
    @Column(length = 1000)
    private String description;

    /** Estado actual de la tarea (TODO, IN_PROGRESS, DONE, etc.). Se almacena como texto en la base de datos. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /** Prioridad de la tarea (LOW, MEDIUM, HIGH, URGENT). Se almacena como texto en la base de datos. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    /** Categoría de la tarea (PERSONAL, ESTUDIOS, TRABAJO). Si pertenece a un proyecto, hereda su categoría. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskCategory category;

    /** Fecha límite de la tarea. Solo almacena la fecha, sin hora. */
    private LocalDate dueDate;

    /** Fecha y hora de creación. Se asigna automáticamente al persistir y no puede modificarse. */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Usuario propietario de la tarea.
     * Siempre se rellena al crear la tarea, tanto si tiene proyecto asociado como si no.
     * Es la única forma de identificar al propietario de una tarea ({@code project = null}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Proyecto al que pertenece la tarea. Es opcional.
     * Si es {@code null}, la tarea no tiene proyecto.
     * Si tiene valor, la tarea pertenece a ese proyecto.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = true)
    private Project project;

    /**
     * Tarea padre de esta subtarea. Es opcional.
     * Si es {@code null}, la tarea es una tarea raíz (no es subtarea de ninguna otra).
     * Si tiene valor, esta tarea es subtarea de la tarea referenciada.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    /**
     * Lista de subtareas de esta tarea.
     * Si la tarea padre es eliminada, todas sus subtareas se eliminan en cascada.
     * Excluida de {@code toString()}, {@code equals()} y {@code hashCode()} para evitar recursión infinita.
     */
    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> subTasks;

    /**
     * Indica si la tarea ha sido enviada a la papelera.
     * Cuando es {@code true}, la tarea no aparece en las listas normales.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Fecha y hora en que la tarea fue enviada a la papelera.
     * Se usa junto con las preferencias del usuario para calcular
     * cuándo debe eliminarse definitivamente.
     */
    private LocalDateTime deletedAt;

    /**
     * Asigna automáticamente la fecha de creación antes de persistir la entidad.
     * Si no se especifican estado o prioridad, se inicializan con los valores por defecto:
     * {@code status = TODO} y {@code priority = MEDIUM}.
     */
    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = TaskStatus.TODO;
        if (this.priority == null) this.priority = TaskPriority.MEDIUM;
    }
}
