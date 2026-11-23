package com.taskmaster.taskmasterbackend.dto.transfer;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Raíz del fichero de exportación e importación de datos de una cuenta (versión 1).
 *
 * <p>El formato es anidado: proyecto → tareas → subtareas → registros de trabajo. La
 * jerarquía no necesita referencias, así que no puede tener huecos ni ciclos; solo las
 * dependencias entre tareas usan la referencia local {@link TaskData#ref()}. El fichero
 * no contiene ids de la base de datos, usuario, email, contraseña, ajustes ni avatar.</p>
 *
 * @param format       identificador del formato; siempre {@link #FORMAT}
 * @param version      versión del formato; siempre {@link #VERSION}
 * @param exportedAt   momento de la exportación (informativo; se ignora al importar)
 * @param projects     proyectos con sus tareas
 * @param tasks        tareas personales (sin proyecto)
 * @param dependencies dependencias entre tareas, por referencia local
 * @author Carlos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataExportFile(
        String format,
        Integer version,
        LocalDateTime exportedAt,
        List<ProjectData> projects,
        List<TaskData> tasks,
        List<DependencyData> dependencies
) {

    /** Valor del campo {@code format} de un fichero de TaskMaster. */
    public static final String FORMAT = "taskmaster-export";

    /** Versión del formato que entienden la exportación y la importación. */
    public static final int VERSION = 1;
}
