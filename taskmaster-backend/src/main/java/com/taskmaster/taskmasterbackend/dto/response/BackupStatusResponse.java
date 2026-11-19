package com.taskmaster.taskmasterbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO de respuesta con el estado de la copia de seguridad en este equipo.
 *
 * <p>Lo usa la sección de copia de seguridad de Ajustes para activar o desactivar
 * los botones y mostrar los avisos de restauración pendiente o fallida.</p>
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

    /**
     * Indica si la última restauración no se pudo aplicar (falló o se descartó al arrancar)
     * y se mantuvieron los datos anteriores. Se descarta con {@code DELETE /api/backup/restore}
     * o al preparar otra restauración.
     */
    private boolean lastRestoreFailed;
}
