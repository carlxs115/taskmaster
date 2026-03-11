package com.taskmaster.repository;

import com.taskmaster.model.Task;
import com.taskmaster.model.TaskCategory;
import com.taskmaster.model.TaskPriority;
import com.taskmaster.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REPOSITORIO DE TASK
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Todas las tareas raíz de un proyecto (no eliminadas y sin tarea padre).
     * Spring genera: SELECT * FROM tasks WHERE project_id = ? AND parent_task_id IS NULL AND deleted = false
     */
    List<Task> findByProjectIdAndParentTaskIsNullAndDeletedFalse(Long projectId);

    /**
     * Subtareas activas de una tarea concreta.
     * Spring genera: SELECT * FROM tasks WHERE parent_task_id = ? AND deleted = false
     */
    List<Task> findByParentTaskIdAndDeletedFalse(Long parentTaskId);

    /**
     * Filtro por estado dentro de un proyecto - solo tareas activas.
     * Spring genera: SELECT * FROM tasks WHERE project_id = ? AND status = ? AND deleted = false
     */
    List<Task> findByProjectIdAndStatusAndDeletedFalse(Long projectId, TaskStatus status);

    /**
     * Filtro por prioridad dentro de un proyecto - solo tareas activas.
     * Spring genera: SELECT * FROM tasks WHERE project_id = ? AND priority = ? AND deleted = false
     */
    List<Task> findByProjectIdAndPriorityAndDeletedFalse(Long projectId, TaskPriority priority);

    /**
     * Comprueba si una tarea tiene subtareas pendientes.
     * Útil para la regla de negocio: no se puede completar una tarea
     * si tiene subtareas en estado TODO o IN_PROGRESS.
     *
     * Spring genera: SELECT COUNT(*) > 0 FROM tasks WHERE parent_task_id = ? AND status != ? AND deleted = false
     */
    boolean existsByParentTaskIdAndStatusNotAndDeletedFalse(Long parentTaskId, TaskStatus status);

    /**
     * PAPELERA ÚNICA POR USUARIO - tareas eliminadas.
     * Navega la relación Task -> Project -> User para obtener todas las tareas
     * eliminadas del usuario sin importar el proyecto.
     * Spring genera: SELECT * FROM tasks t
     *                JOIN projects p ON t.project_id = p.id
     *                WHERE p.user_id = ? AND t.deleted = true
     */
    List<Task> findByProjectUserIdAndDeletedTrue(Long userId);

    /**
     * PAPELERA ÚNICA POR USUARIO — proyectos eliminados.
     * Se usa para el vaciado automático según el periodo configurado.
     * Spring genera: SELECT * FROM tasks WHERE deleted = true AND deleted_at < ?
     */
    List<Task> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoffDate);

    /**
     * Tareas personales activas del usuario (sin proyecto).
     * Navega Task -> project es null, y filtra por usuario a través
     * de otra relación. Como no hay proyecto, filtramos por usuario
     * directamente - lo haremos en el servicio.
     * Spring genera: SELECT * FROM tasks WHERE project_id IS NULL AND deleted = false
     */
    List<Task> findByProjectIsNullAndDeletedFalse();

    /**
     * Tareas personales en papelera (sin proyecto).
     */
    List<Task> findByProjectIsNullAndDeletedTrue();

    /**
     * Tareas activas de una categoría concreta sin proyecto.
     * Spring genera: SELECT * FROM tasks
     *                WHERE category = ? AND project_id IS NULL AND deleted = false
     */
    List<Task> findByCategoryAndProjectIsNullAndDeletedFalse(TaskCategory category);

    /**
     * Todas las tareas activas de una categoría (con y sin proyecto).
     * Spring genera: SELECT * FROM tasks WHERE category = ? AND deleted = false
     */
    List<Task> findByCategoryAndDeletedFalse(TaskCategory category);
}
