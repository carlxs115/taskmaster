package com.taskmaster.repository;

import com.taskmaster.model.Task;
import com.taskmaster.model.TaskPriority;
import com.taskmaster.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITORIO DE TASK
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Todas las tareas raíz de un proyecto (sin tarea padre).
     * Spring genera: SELECT * FROM tasks WHERE project_id = ? AND parent_task_id IS NULL
     */
    List<Task> findByProjectIdAndParentTaskIsNull(Long projectId);

    /**
     * Subtareas de una tarea concreta.
     * Spring genera: SELECT * FROM tasks WHERE parent_task_id = ?
     */
    List<Task> findByParentTaskId(Long parentTaskId);

    /**
     * Filtro por estado dentro de un proyecto.
     * Spring genera: SELECT * FROM tasks WHERE project_id = ? AND status = ?
     */
    List<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status);

    /**
     * Filtro por prioridad dentro de un proyecto.
     * Spring genera: SELECT * FROM tasks WHERE project_id = ? AND priority = ?
     */
    List<Task> findByProjectIdAndPriority(Long projectId, TaskPriority priority);

    /**
     * Comprueba si una tarea tiene subtareas pendientes.
     * Útil para la regla de negocio: no se puede completar una tarea
     * si tiene subtareas en estado TODO o IN_PROGRESS.
     */
    boolean existsByParentTaskIdAndStatusNot(Long parentTaskId, TaskStatus status);
}
