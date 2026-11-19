package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Componente que limpia, una vez al arrancar, las dependencias entre tareas que no
 * pueden ser válidas: las que apuntan a una tarea inexistente y las que enlazan
 * tareas de usuarios distintos.
 *
 * <p>Elimina las filas que dejaron las versiones anteriores al arreglo de SEG-8, en las
 * que el borrado físico de una tarea no quitaba las filas en las que era predecesora.
 * Desde SEG-8 cada borrado físico las limpia en su propia transacción, así que no
 * deberían aparecer nuevas y no se programa a medianoche.</p>
 *
 * <p>Limpia también los registros de trabajo que apuntan a una tarea inexistente, dejados
 * por versiones anteriores al arreglo de SEG-9. No llama a
 * {@code deleteOrphanTaskReferences} para registrar cada limpieza por separado.</p>
 *
 * @author Carlos
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDependencyCleanup {

    private final TaskRepository taskRepository;

    /**
     * Ejecuta la limpieza en cuanto el backend termina de arrancar.
     * Un fallo aquí se registra y no impide que el backend arranque.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanOnStartup() {
        try {
            int eliminadas = taskRepository.deleteInvalidDependencies();
            if (eliminadas > 0) {
                log.warn("Eliminadas {} dependencias inválidas entre tareas", eliminadas);
            }
        } catch (RuntimeException e) {
            // Un fallo aquí no debe impedir que el backend arranque; se reintentará en el próximo arranque
            log.error("Falló la limpieza de dependencias entre tareas al arrancar", e);
        }

        // Bloque independiente: el fallo de una limpieza no impide la otra
        try {
            int eliminados = taskRepository.deleteOrphanWorkLogs();
            if (eliminados > 0) {
                log.warn("Eliminados {} registros de trabajo huérfanos", eliminados);
            }
        } catch (RuntimeException e) {
            // Igual que arriba: no debe impedir el arranque y se reintentará en el próximo
            log.error("Falló la limpieza de registros de trabajo huérfanos al arrancar", e);
        }
    }
}
