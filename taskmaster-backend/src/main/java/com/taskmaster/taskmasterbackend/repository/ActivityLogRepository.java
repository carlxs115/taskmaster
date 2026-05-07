package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.ActivityLog;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link ActivityLog}.
 *
 * <p>Proporciona consultas para recuperar y limpiar registros de actividad
 * filtrados por usuario, tipo de acción y entidad afectada.</p>
 *
 * <p>Todos los métodos de modificación ({@code delete*}) son transaccionales
 * por defecto gracias a Spring Data JPA.</p>
 *
 * @author Carlos
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Devuelve los registros de actividad de un usuario filtrados por tipos de acción,
     * ordenados por fecha descendente.
     * Se usa para separar el historial de actividad del historial de accesos.
     *
     * @param userId      identificador del usuario
     * @param actionTypes lista de tipos de acción por los que filtrar
     * @return lista de registros filtrada y ordenada del más reciente al más antiguo
     */
    List<ActivityLog> findByUserIdAndActionTypeInOrderByCreatedAtDesc(
            Long userId, List<ActionType> actionTypes);

    /**
     * Devuelve los registros de actividad de un usuario para una entidad concreta,
     * ordenados por fecha descendente.
     * Se usa para mostrar el historial de una tarea o proyecto específico.
     *
     * @param userId     identificador del usuario
     * @param entityType tipo de entidad ({@code "TASK"}, {@code "PROJECT"}, etc.)
     * @param entityId   identificador de la entidad
     * @return lista de registros de esa entidad ordenada del más reciente al más antiguo
     */
    List<ActivityLog> findByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            Long userId, String entityType, Long entityId);

    /**
     * Devuelve los registros de actividad de un usuario para un conjunto de entidades
     * del mismo tipo, ordenados por fecha descendente.
     * Se usa para cargar el historial completo de un proyecto con todas sus tareas.
     *
     * @param userId     identificador del usuario
     * @param entityType tipo de entidad
     * @param entityIds  lista de identificadores de entidades a consultar
     * @return lista de registros ordenada del más reciente al más antiguo
     */
    List<ActivityLog> findByUserIdAndEntityTypeAndEntityIdInOrderByCreatedAtDesc(
            Long userId, String entityType, List<Long> entityIds);

    /**
     * Elimina todos los registros de actividad anteriores a una fecha dada.
     * Llamado por {@link com.taskmaster.taskmasterbackend.service.ActivityLogService#deleteOldEntries}
     * cada día a las 3:00 AM para evitar que la tabla crezca indefinidamente.
     *
     * @param cutoff fecha límite; se eliminan los registros anteriores a esta fecha
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoff);

    /**
     * Elimina todos los registros de actividad asociados a un usuario.
     * Llamado desde {@link com.taskmaster.taskmasterbackend.service.UserService#deleteAccount}
     * antes de eliminar la cuenta para limpiar datos huérfanos.
     *
     * @param userId identificador del usuario cuyos registros se eliminarán
     */
    void deleteByUserId(Long userId);
}