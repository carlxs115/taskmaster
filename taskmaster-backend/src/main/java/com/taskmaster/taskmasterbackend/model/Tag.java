package com.taskmaster.taskmasterbackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

/**
 * ENTIDAD TAG (Etiqueta)
 *
 * Las etiquetas permiten categorizar tareas libremente.
 *
 * Relación ManyToMany con Task:
 * - Una etiqueta puede estar en muchas tareas
 * - Una tarea puede tener muchas etiquetas
 * La tabla intermedia "task_tags" ya está definida en Task.java
 */
@Entity
@Table(name = "tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    /**
     * @Id                  → Es la clave primaria de la tabla
     * @GeneratedValue      → La BD genera el valor automáticamente (autoincremental)
     * GenerationType.IDENTITY → Delega la generación al motor de BD (H2, PostgreSQL...)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre de la etiqueta.
     * unique = true → no puede haber dos etiquetas con el mismo nombre
     */
    @Column(unique = true, nullable = false)
    @NotBlank(message = "El nombre de la etiqueta es obligatorio")
    private String name;

    /**
     * RELACIÓN CON TASK (ManyToMany)
     *
     * mappedBy = "tags" → la relación y la tabla intermedia ya están
     * definidas en Task.java, aquí solo la referenciamos.
     *
     * No ponemos cascade aquí — si borramos una etiqueta no queremos
     * que se borren las tareas asociadas.
     */
    @ManyToMany(mappedBy = "tags")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> tasks;
}
