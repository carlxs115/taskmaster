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

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    // Método principal para registrar cualquier evento
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

    // Sobrecarga simplificada para eventos sin valores old/new
    public void log(Long userId, ActionType actionType,
                    String entityType, Long entityId, String entityName) {
        log(userId, actionType, entityType, entityId, entityName, null, null);
    }

    // Sobrecarga para eventos de perfil/auth sin entidad concreta
    public void log(Long userId, ActionType actionType) {
        log(userId, actionType, null, null, null, null, null);
    }

    // Obtener historial de actividad (excluye LOGIN/LOGOUT)
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
                        ActionType.PROFILE_UPDATED, ActionType.PASSWORD_CHANGED
                )
        );
    }

    // Obtener solo accesos (LOGIN/LOGOUT) para la sección Seguridad
    public List<ActivityLog> getAccessHistory(Long userId) {
        return activityLogRepository.findByUserIdAndActionTypeInOrderByCreatedAtDesc(
                userId,
                List.of(ActionType.LOGIN, ActionType.LOGOUT)
        );
    }

    public List<ActivityLog> getEntityHistory(Long userId, String entityType, Long entityId) {
        return activityLogRepository
                .findByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(userId, entityType, entityId);
    }

    // Limpieza automática: corre cada día a las 3:00 AM
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        activityLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("ActivityLog limpiado: entradas anteriores a {}", cutoff);
    }
}
