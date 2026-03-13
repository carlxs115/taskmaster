package com.taskmaster.repository;

import com.taskmaster.model.Task;
import com.taskmaster.model.TaskCategory;
import com.taskmaster.model.TaskPriority;
import com.taskmaster.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REPOSITORIO DE TASK
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ── Tareas de proyecto ────────────────────────────────────────────────────

    List<Task> findByProjectIdAndParentTaskIsNullAndDeletedFalse(Long projectId);

    List<Task> findByParentTaskIdAndDeletedFalse(Long parentTaskId);

    List<Task> findByProjectIdAndStatusAndDeletedFalse(Long projectId, TaskStatus status);

    List<Task> findByProjectIdAndPriorityAndDeletedFalse(Long projectId, TaskPriority priority);

    boolean existsByParentTaskIdAndStatusNotAndDeletedFalse(Long parentTaskId, TaskStatus status);


    // ── Tareas personales (sin proyecto) filtradas por usuario ────────────────

    /** Reemplaza findByProjectIsNullAndDeletedFalse() — ahora filtra por usuario */
    List<Task> findByUserIdAndProjectIsNullAndDeletedFalse(Long userId);

    /** Reemplaza findByProjectIsNullAndDeletedTrue() — ahora filtra por usuario */
    List<Task> findByUserIdAndProjectIsNullAndDeletedTrue(Long userId);

    /** Reemplaza findByCategoryAndProjectIsNullAndDeletedFalse() — ahora filtra por usuario */
    List<Task> findByUserIdAndCategoryAndProjectIsNullAndDeletedFalse(Long userId, TaskCategory category);


    // ── Papelera ──────────────────────────────────────────────────────────────

    /** Tareas de proyectos del usuario en papelera */
    List<Task> findByProjectUserIdAndDeletedTrue(Long userId);

    /** Para el vaciado automático del scheduler */
    List<Task> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoffDate);


    // ── Queries que ya no se usan (mantenidas por compatibilidad) ─────────────

    /** @deprecated Usa findByUserIdAndProjectIsNullAndDeletedFalse */
    @Deprecated
    List<Task> findByProjectIsNullAndDeletedFalse();

    /** @deprecated Usa findByUserIdAndProjectIsNullAndDeletedTrue */
    @Deprecated
    List<Task> findByProjectIsNullAndDeletedTrue();

    /** @deprecated Usa findByUserIdAndCategoryAndProjectIsNullAndDeletedFalse */
    @Deprecated
    List<Task> findByCategoryAndProjectIsNullAndDeletedFalse(TaskCategory category);

    List<Task> findByCategoryAndDeletedFalse(TaskCategory category);}
