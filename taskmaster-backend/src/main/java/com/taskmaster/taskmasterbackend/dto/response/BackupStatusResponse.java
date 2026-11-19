package com.taskmaster.taskmasterbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO de respuesta con el estado de la copia de seguridad en este equipo.
 *
 * <p>Lo usa la sección de copia de seguridad de Ajustes para activar o desactivar
 * los botones y mostrar el aviso de restauración pendiente.</p>
 *
 * @author Carlos
 */
@Data
@AllArgsConstructor
public class BackupStatusResponse {

    /**
     * Indica si se pueden crear y restaurar copias: las rutas de datos se conocen
     * y la base de datos tiene una única cuenta.
     */
    private boolean available;

    /** Indica si hay una restauración preparada que se aplicará en el siguiente arranque. */
    private boolean restorePending;
}
