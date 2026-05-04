package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO ligero para representar una tarea en la vista de calendario.
 *
 * <p>Solo incluye los campos necesarios para renderizar el chip
 * en el grid mensual: identificador, título, fecha límite, estado y prioridad.</p>
 *
 * @author Carlos
 */
@Data
@AllArgsConstructor
public class CalendarTaskDTO {
    private Long id;
    private String title;
    private LocalDate dueDate;
    private TaskStatus status;
    private TaskPriority priority;
}
