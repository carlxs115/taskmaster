package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.model.ActivityLog;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.repository.ActivityLogRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio que gestiona el registro de actividad de los usuarios.
 *
 * <p>Proporciona métodos sobrecargados para registrar eventos con distintos
 * niveles de detalle, así como consultas para recuperar el historial de
 * actividad y accesos. Incluye una tarea programada de limpieza automática.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    /**
     * Registra un evento de actividad con todos sus detalles.
     *
     * @param userId     identificador del usuario que realizó la acción
     * @param actionType tipo de acción realizada
     * @param entityType tipo de entidad afectada ({@code "TASK"}, {@code "PROJECT"}, etc.)
     * @param entityId   identificador de la entidad afectada
     * @param entityName nombre de la entidad en el momento de la acción (snapshot)
     * @param oldValue   valor anterior del campo modificado, o {@code null} si no aplica
     * @param newValue   nuevo valor del campo modificado, o {@code null} si no aplica
     */
    public void log(Long userId, ActionType actionType, String entityType, Long entityId,
                    String entityName, String oldValue, String newValue) {

        User user = userRepository.getReferenceById(userId);

        ActivityLog entry = ActivityLog.builder()
                .user(user)
                .actionType(actionType)
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();

        activityLogRepository.save(entry);
    }

    /**
     * Registra un evento de actividad sin valores anterior ni nuevo.
     * Útil para acciones que no implican cambio de estado (crear, editar, eliminar).
     *
     * @param userId     identificador del usuario
     * @param actionType tipo de acción realizada
     * @param entityType tipo de entidad afectada
     * @param entityId   identificador de la entidad afectada
     * @param entityName nombre de la entidad en el momento de la acción
     */
    public void log(Long userId, ActionType actionType,
                    String entityType, Long entityId, String entityName) {
        log(userId, actionType, entityType, entityId, entityName, null, null);
    }

    /**
     * Registra un evento de actividad sin entidad asociada.
     * Útil para acciones de perfil o autenticación (login, logout, cambio de contraseña).
     *
     * @param userId     identificador del usuario
     * @param actionType tipo de acción realizada
     */
    public void log(Long userId, ActionType actionType) {
        log(userId, actionType, null, null, null, null, null);
    }

    /**
     * Devuelve el historial de actividad de un usuario, excluyendo eventos de autenticación.
     *
     * @param userId identificador del usuario
     * @return lista de registros ordenada del más reciente al más antiguo
     */
    public List<ActivityLog> getActivityHistory(Long userId) {
        return activityLogRepository.findByUserIdAndActionTypeInOrderByCreatedAtDesc(
                userId,
                List.of(
                        ActionType.TASK_CREATED, ActionType.TASK_EDITED,
                        ActionType.TASK_DELETED, ActionType.TASK_PERMANENTLY_DELETED,
                        ActionType.TASK_RESTORED, ActionType.TASK_STATUS_CHANGED,
                        ActionType.SUBTASK_CREATED, ActionType.SUBTASK_EDITED,
                        ActionType.SUBTASK_DELETED,
                        ActionType.PROJECT_CREATED, ActionType.PROJECT_EDITED,
                        ActionType.PROJECT_DELETED, ActionType.PROJECT_PERMANENTLY_DELETED,
                        ActionType.PROJECT_RESTORED, ActionType.PROJECT_STATUS_CHANGED,
                        ActionType.PROFILE_UPDATED
                )
        );
    }

    /**
     * Devuelve el historial de accesos de un usuario (solo eventos LOGIN y LOGOUT).
     * Se usa en la sección de seguridad del perfil.
     *
     * @param userId identificador del usuario
     * @return lista de registros de acceso ordenada del más reciente al más antiguo
     */
    public List<ActivityLog> getAccessHistory(Long userId) {
        return activityLogRepository.findByUserIdAndActionTypeInOrderByCreatedAtDesc(
                userId,
                List.of(ActionType.LOGIN, ActionType.LOGOUT, ActionType.PASSWORD_CHANGED)
        );
    }

    /**
     * Devuelve el historial de actividad de una entidad concreta.
     *
     * @param userId     identificador del usuario
     * @param entityType tipo de entidad ({@code "TASK"}, {@code "PROJECT"}, etc.)
     * @param entityId   identificador de la entidad
     * @return lista de registros de esa entidad ordenada por fecha descendente
     */
    public List<ActivityLog> getEntityHistory(Long userId, String entityType, Long entityId) {
        return activityLogRepository
                .findByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(userId, entityType, entityId);
    }

    /**
     * Elimina automáticamente los registros de actividad con más de 7 días de antigüedad.
     * Se ejecuta cada día a las 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        activityLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("ActivityLog limpiado: entradas anteriores a {}", cutoff);
    }

    /**
     * Devuelve todos los registros de actividad de un usuario para una lista de entidades
     * del mismo tipo, ordenados por fecha descendente.
     *
     * @param userId     identificador del usuario
     * @param entityType tipo de entidad
     * @param entityIds  lista de identificadores de entidades
     * @return lista de registros ordenada por fecha
     */
    public List<ActivityLog> getHistoryForEntities(Long userId, String entityType, List<Long> entityIds) {
        if (entityIds.isEmpty()) return List.of();
        return activityLogRepository
                .findByUserIdAndEntityTypeAndEntityIdInOrderByCreatedAtDesc(userId, entityType, entityIds);
    }
}
