package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;

import java.time.LocalDate;

/**
 * DTO ligero para representar una tarea en la vista de calendario.
 *
 * <p>Solo incluye los campos necesarios para renderizar el chip
 * en el grid mensual: identificador, título, fecha límite, estado y prioridad.
 * Al ser un {@code record}, es inmutable por diseño y no necesita
 * anotaciones de Lombok.</p>
 *
 * @param id       identificador único de la tarea
 * @param title    título de la tarea
 * @param dueDate  fecha límite de la tarea
 * @param status   estado actual de la tarea
 * @param priority prioridad de la tarea
 *
 * @author Carlos
 */
public record CalendarTaskDTO(
        Long id,
        String title,
        LocalDate dueDate,
        TaskStatus status,
        TaskPriority priority
) {}
