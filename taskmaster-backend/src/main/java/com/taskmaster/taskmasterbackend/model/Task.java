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
 * <p>Una tarea puede ser independiente (sin proyecto) o pertenecer a un proyecto.
 * También puede actuar como tarea padre conteniendo subtareas, que son a su vez
 * instancias de {@code Task} enlazadas mediante una relación recursiva
 * ({@code parentTask} / {@code subTasks}).</p>
 *
 * <p>Soporta borrado lógico (soft delete) mediante el campo {@code deleted},
 * lo que permite enviar tareas a la papelera antes de eliminarlas definitivamente.</p>
 *
 * @author Carlos
 */
@Entity
@Table(
        name = "tasks",

        // Índice en user_id porque todas las consultas de tareas filtran por usuario.
        // Índice en project_id porque se consultan frecuentemente las tareas de un proyecto.
        indexes = {
                @Index(name = "idx_tasks_user_id",    columnList = "user_id"),
                @Index(name = "idx_tasks_project_id", columnList = "project_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    /** Identificador único de la tarea, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Título de la tarea. No puede estar vacío.
     * La validación real se realiza en el DTO de request con {@code @Valid}.
     */
    @Column(nullable = false)
    @NotBlank(message = "El título de la tarea es obligatorio")
    private String title;

    /**
     * Descripción opcional de la tarea.
     * Máximo 1000 caracteres a nivel de base de datos.
     */
    @Column(length = 1000)
    private String description;

    /**
     * Estado actual de la tarea.
     * Se almacena como texto en BD para legibilidad.
     * Valor por defecto: {@link TaskStatus#TODO}, inicializado con {@code @Builder.Default}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    /**
     * Prioridad de la tarea.
     * Se almacena como texto en BD para legibilidad.
     * Valor por defecto: {@link TaskPriority#MEDIUM}, inicializado con {@code @Builder.Default}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    /**
     * Categoría de la tarea (PERSONAL, ESTUDIOS, TRABAJO).
     * Si pertenece a un proyecto, normalmente hereda la categoría de éste.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskCategory category;

    /**
     * Fecha límite de la tarea. Solo almacena la fecha, sin hora.
     * Es opcional: puede ser {@code null} si la tarea no tiene fecha límite.
     */
    private LocalDate dueDate;

    /**
     * Fecha y hora de creación de la tarea.
     * Se asigna automáticamente en {@link #onCreate()} y nunca puede modificarse.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Usuario propietario de la tarea.
     * Siempre se rellena al crear la tarea, tanto si tiene proyecto como si no.
     * Es la única forma de identificar al propietario cuando {@code project} es {@code null}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Proyecto al que pertenece la tarea. Es opcional ({@code null} = tarea independiente).
     * nullable es el valor por defecto en @JoinColumn, se omite para evitar redundancia.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /**
     * Tarea padre de esta subtarea. Es opcional.
     * Si es {@code null}, esta tarea es una tarea raíz (no es subtarea de ninguna otra).
     * Si tiene valor, esta tarea es subtarea de la tarea referenciada.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    /**
     * Lista de subtareas de esta tarea (relación recursiva).
     * Si la tarea padre se elimina, todas sus subtareas se eliminan en cascada.
     *
     * <p>{@code @ToString.Exclude} y {@code @EqualsAndHashCode.Exclude} son
     * obligatorios aquí para evitar recursión infinita, ya que cada subtarea
     * tiene a su vez una lista de subtareas.</p>
     */
    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> subTasks;

    /**
     * Indica si la tarea ha sido enviada a la papelera (soft delete).
     * Cuando es {@code true}, la tarea no aparece en las listas normales.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Fecha y hora en que la tarea fue enviada a la papelera.
     * {@code TrashScheduler} la usa junto con {@code trashRetentionDays}
     * para calcular cuándo eliminarla definitivamente.
     * Es {@code null} si la tarea no está en la papelera.
     */
    private LocalDateTime deletedAt;

    /**
     * Asigna automáticamente la fecha de creación antes de persistir la entidad.
     *
     * <p><b>Nota:</b> los valores por defecto de {@code status} y {@code priority}
     * se gestionan con {@code @Builder.Default} directamente en los campos,
     * lo que garantiza que también se aplican al construir el objeto con el builder
     * sin necesidad de persistirlo.</p>
     */
    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = TaskStatus.TODO;
        if (this.priority == null) this.priority = TaskPriority.MEDIUM;
    }
}
