package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.ActivityLog;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link ActivityLog}.
 *
 * <p>Proporciona consultas para recuperar y limpiar registros de actividad
 * filtrados por usuario, tipo de acción y entidad afectada.</p>
 *
 * @author Carlos
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Devuelve los registros de actividad de un usuario filtrados por tipos de acción,
     * ordenados por fecha descendente.
     *
     * @param userId      identificador del usuario
     * @param actionTypes lista de tipos de acción por los que filtrar
     * @return lista de registros filtrada y ordenada
     */
    List<ActivityLog> findByUserIdAndActionTypeInOrderByCreatedAtDesc(
            Long userId, List<ActionType> actionTypes);

    /**
     * Devuelve los registros de actividad de un usuario para una entidad concreta,
     * ordenados por fecha descendente.
     *
     * @param userId     identificador del usuario
     * @param entityType tipo de entidad ({@code "TASK"}, {@code "PROJECT"}, etc.)
     * @param entityId   identificador de la entidad
     * @return lista de registros de esa entidad ordenada por fecha
     */
    List<ActivityLog> findByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            Long userId, String entityType, Long entityId);

    /**
     * Elimina todos los registros de actividad anteriores a una fecha dada.
     * Se usa para la limpieza periódica del historial.
     *
     * @param cutoff fecha límite; se eliminarán los registros anteriores a esta fecha
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoff);

    /**
     * Elimina todos los registros de actividad asociados a un usuario.
     * Se usa al eliminar permanentemente una cuenta de usuario.
     *
     * @param userId identificador del usuario cuyos registros se eliminarán
     */
    @Transactional
    void deleteByUserId(Long userId);

    /**
     * Devuelve los registros de actividad de un usuario para un conjunto de entidades
     * del mismo tipo, ordenados por fecha descendente.
     *
     * @param userId     identificador del usuario
     * @param entityType tipo de entidad
     * @param entityIds  lista de identificadores de entidades
     * @return lista de registros ordenada por fecha
     */
    List<ActivityLog> findByUserIdAndEntityTypeAndEntityIdInOrderByCreatedAtDesc(
            Long userId, String entityType, List<Long> entityIds);
}
