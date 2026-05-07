package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link Task}.
 *
 * <p>Proporciona consultas para recuperar tareas de proyecto, tareas personales,
 * subtareas, elementos en papelera y estadísticas de usuario.</p>
 *
 * <p>Todas las queries se derivan automáticamente por Spring Data JPA a partir
 * del nombre del método, sin necesidad de JPQL explícito.</p>
 *
 * @author Carlos
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // -------------------------------------------------------------------------
    // Tareas de proyecto
    // -------------------------------------------------------------------------

    /**
     * Devuelve las tareas raíz activas de un proyecto (sin tarea padre).
     * Usa el índice {@code idx_tasks_project_id} definido en la entidad.
     *
     * @param projectId identificador del proyecto
     * @return lista de tareas raíz activas del proyecto
     */
    List<Task> findByProjectIdAndParentTaskIsNullAndDeletedFalse(Long projectId);

    /**
     * Devuelve todas las tareas raíz de un proyecto, incluyendo las eliminadas.
     * Se usa para cargar el historial de actividad completo del proyecto,
     * ya que las tareas eliminadas siguen teniendo registros en el log.
     *
     * @param projectId identificador del proyecto
     * @return lista de todas las tareas raíz del proyecto, activas y eliminadas
     */
    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId AND t.parentTask IS NULL")
    List<Task> findByProjectIdAndParentTaskIsNull(@Param("projectId") Long projectId);

    /**
     * Devuelve las tareas activas de un proyecto filtradas por estado.
     *
     * @param projectId identificador del proyecto
     * @param status    estado por el que filtrar
     * @return lista de tareas activas con ese estado
     */
    List<Task> findByProjectIdAndStatusAndDeletedFalse(Long projectId, TaskStatus status);

    /**
     * Devuelve las tareas activas de un proyecto filtradas por prioridad.
     *
     * @param projectId identificador del proyecto
     * @param priority  prioridad por la que filtrar
     * @return lista de tareas activas con esa prioridad
     */
    List<Task> findByProjectIdAndPriorityAndDeletedFalse(Long projectId, TaskPriority priority);

    /**
     * Comprueba si un proyecto tiene tareas activas con un estado distinto a los indicados.
     * Se usa en {@code ProjectService.updateProject} para validar que todas las tareas
     * estén completadas o canceladas antes de marcar el proyecto como {@code DONE}.
     *
     * @param projectId identificador del proyecto
     * @param statuses  lista de estados a excluir de la comprobación
     * @return {@code true} si existe alguna tarea activa con estado distinto
     */
    boolean existsByProjectIdAndStatusNotInAndDeletedFalse(Long projectId, List<TaskStatus> statuses);

    // -------------------------------------------------------------------------
    // Tareas personales (sin proyecto)
    // -------------------------------------------------------------------------

    /**
     * Devuelve las tareas raíz activas de un usuario sin proyecto ni tarea padre.
     * Usa el índice {@code idx_tasks_user_id} definido en la entidad.
     *
     * @param userId identificador del usuario
     * @return lista de tareas personales raíz activas
     */
    List<Task> findByUserIdAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(Long userId);

    /**
     * Devuelve las tareas raíz activas de un usuario filtradas por categoría,
     * excluyendo las que pertenecen a un proyecto.
     *
     * @param userId   identificador del usuario
     * @param category categoría por la que filtrar
     * @return lista de tareas personales raíz activas con esa categoría
     */
    List<Task> findByUserIdAndCategoryAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(
            Long userId, TaskCategory category);

    /**
     * Devuelve las tareas activas de un usuario con {@code dueDate} en un rango de fechas.
     * Excluye subtareas y tareas eliminadas. Se usa para poblar la vista de calendario.
     *
     * @param userId    identificador del usuario
     * @param startDate inicio del rango (inclusive), primer día del mes
     * @param endDate   fin del rango (inclusive), último día del mes
     * @return lista de tareas con fecha límite en ese rango
     */
    List<Task> findByUserIdAndDueDateBetweenAndParentTaskIsNullAndDeletedFalse(
            Long userId, LocalDate startDate, LocalDate endDate);

    // -------------------------------------------------------------------------
    // Subtareas
    // -------------------------------------------------------------------------

    /**
     * Devuelve las subtareas activas de una tarea padre.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de subtareas activas
     */
    List<Task> findByParentTaskIdAndDeletedFalse(Long parentTaskId);

    /**
     * Devuelve todas las subtareas de una tarea padre, incluyendo las eliminadas.
     * Se usa para cargar el historial de actividad completo de una tarea.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de todas las subtareas, activas y eliminadas
     */
    List<Task> findByParentTaskId(Long parentTaskId);

    /**
     * Comprueba si una tarea padre tiene subtareas activas con un estado distinto al indicado.
     * Se usa en {@code TaskService.changeStatus} para validar que todas las subtareas
     * estén completadas antes de marcar la tarea padre como {@code DONE}.
     *
     * @param parentTaskId identificador de la tarea padre
     * @param status       estado a excluir de la comprobación (normalmente {@code DONE})
     * @return {@code true} si existe alguna subtarea activa con estado distinto
     */
    boolean existsByParentTaskIdAndStatusNotAndDeletedFalse(Long parentTaskId, TaskStatus status);

    /**
     * Comprueba si una tarea padre tiene subtareas activas con un estado distinto a los indicados.
     * Se usa en {@code TaskService.updateTask} para validar que todas las subtareas
     * estén completadas o canceladas antes de marcar la tarea padre como {@code DONE}.
     *
     * @param parentTaskId identificador de la tarea padre
     * @param statuses     lista de estados a excluir (normalmente {@code DONE} y {@code CANCELLED})
     * @return {@code true} si existe alguna subtarea activa con estado distinto
     */
    boolean existsByParentTaskIdAndStatusNotInAndDeletedFalse(Long parentTaskId, List<TaskStatus> statuses);

    // -------------------------------------------------------------------------
    // Papelera
    // -------------------------------------------------------------------------

    /**
     * Devuelve las tareas personales en papelera de un usuario (sin proyecto).
     *
     * @param userId identificador del usuario
     * @return lista de tareas personales eliminadas
     */
    List<Task> findByUserIdAndProjectIsNullAndDeletedTrue(Long userId);

    /**
     * Devuelve las tareas en papelera de un usuario concreto cuya fecha de
     * eliminación es anterior a la indicada.
     * Usado por {@link com.taskmaster.taskmasterbackend.TrashScheduler} para
     * purgar solo las tareas del usuario correspondiente, sin afectar a otros.
     *
     * @param userId     identificador del usuario propietario de las tareas
     * @param cutoffDate fecha límite; se devuelven las tareas eliminadas antes de esta fecha
     * @return lista de tareas del usuario a eliminar definitivamente
     */
    List<Task> findByUserIdAndDeletedTrueAndDeletedAtBefore(Long userId, LocalDateTime cutoffDate);

    /**
     * Devuelve las tareas de proyectos del usuario que están en la papelera.
     * Navega la relación {@code Task → Project → User} para filtrar por usuario.
     *
     * @param userId identificador del usuario propietario del proyecto
     * @return lista de tareas de proyecto eliminadas
     */
    List<Task> findByProjectUserIdAndDeletedTrue(Long userId);

    // -------------------------------------------------------------------------
    // Estadísticas
    // -------------------------------------------------------------------------

    /**
     * Cuenta las tareas activas de un usuario.
     * Usa el índice {@code idx_tasks_user_id} para mayor eficiencia.
     *
     * @param userId identificador del usuario
     * @return número de tareas activas
     */
    long countByUserIdAndDeletedFalse(Long userId);

    /**
     * Cuenta las tareas activas de un usuario con un estado concreto.
     * Llamado múltiples veces desde {@code UserService.getStats}
     * para cada estado (TODO, IN_PROGRESS, DONE, CANCELLED).
     *
     * @param userId identificador del usuario
     * @param status estado por el que filtrar
     * @return número de tareas activas con ese estado
     */
    long countByUserIdAndStatusAndDeletedFalse(Long userId, TaskStatus status);
}
