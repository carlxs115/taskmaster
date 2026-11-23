package com.taskmaster.taskmasterbackend.dto.response;

/**
 * DTO de respuesta con los elementos creados por una importación de datos.
 *
 * @param projects     proyectos creados
 * @param tasks        tareas creadas, subtareas incluidas
 * @param workLogs     registros de trabajo creados
 * @param dependencies dependencias entre tareas creadas
 * @author Carlos
 */
public record ImportResultResponse(int projects, int tasks, int workLogs, int dependencies) {
}
