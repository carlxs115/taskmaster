package com.taskmaster.taskmasterbackend.dto.response;

import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta que representa una tarea.
 *
 * <p>Contiene todos los campos de una tarea que el backend devuelve al frontend,
 * incluyendo información de papelera y referencias a proyecto y tarea padre
 * mediante sus identificadores (no los objetos completos, para evitar
 * referencias circulares en la serialización JSON).</p>
 *
 * @author Carlos
 */
@Data
@Builder
public class TaskResponse {

    /** Identificador único de la tarea. */
    private Long id;

    /** Título de la tarea. */
    private String title;

    /** Descripción de la tarea. */
    private String description;

    /** Estado actual de la tarea. */
    private TaskStatus status;

    /** Prioridad de la tarea. */
    private TaskPriority priority;

    /** Categoría de la tarea (PERSONAL, ESTUDIOS, TRABAJO). */
    private TaskCategory category;

    /**
     * Fecha límite de la tarea.
     * {@code null} si la tarea no tiene fecha límite asignada.
     */
    private LocalDate dueDate;

    /** Fecha y hora de creación de la tarea. */
    private LocalDateTime createdAt;

    /**
     * Identificador del proyecto al que pertenece la tarea.
     * {@code null} si es una tarea personal sin proyecto.
     */
    private Long projectId;

    /**
     * Identificador de la tarea padre.
     * {@code null} si es una tarea raíz (no es subtarea de ninguna otra).
     */
    private Long parentTaskId;

    /**
     * Indica si la tarea está en la papelera (soft delete).
     * Cuando es {@code true}, la tarea no aparece en las listas normales.
     */
    private boolean deleted;

    /**
     * Fecha y hora en que la tarea fue enviada a la papelera.
     * {@code null} si la tarea no está eliminada.
     */
    private LocalDateTime deletedAt;
}
