package com.taskmaster.taskmasterbackend.dto.transfer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Dependencia entre dos tareas del fichero de exportación e importación de datos.
 *
 * @param task      referencia local ({@link TaskData#ref()}) de la tarea que depende
 * @param dependsOn referencia local de la tarea de la que depende
 * @author Carlos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DependencyData(
        String task,
        String dependsOn
) {
}
