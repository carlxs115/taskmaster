package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.ActivityLog;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ActivityLog> findByUserIdAndActionTypeInOrderByCreatedAtDesc(
            Long userId, List<ActionType> actionTypes);

    List<ActivityLog> findByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            Long userId, String entityType, Long entityId);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
