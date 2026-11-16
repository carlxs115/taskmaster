package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link Task}.
 *
 * <p>Proporciona consultas para recuperar tareas de proyecto, tareas personales,
 * subtareas, elementos en papelera y estadísticas de usuario.</p>
 *
 * <p>La mayoría de las queries se derivan automáticamente por Spring Data JPA a
 * partir del nombre del método; las que no se pueden expresar así usan
 * {@code @Query} (JPQL o, para las tablas {@code task_dependencies} y {@code work_logs}, SQL nativo).</p>
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
     * Excluye subtareas, tareas eliminadas y tareas de proyectos en la papelera.
     * Se usa para poblar la vista de calendario.
     *
     * <p>El {@code LEFT JOIN} conserva las tareas personales, que no tienen proyecto.</p>
     *
     * @param userId    identificador del usuario
     * @param startDate inicio del rango (inclusive), primer día del mes
     * @param endDate   fin del rango (inclusive), último día del mes
     * @return lista de tareas con fecha límite en ese rango
     */
    @Query("SELECT t FROM Task t LEFT JOIN t.project p " +
            "WHERE t.user.id = :userId AND t.dueDate BETWEEN :startDate AND :endDate " +
            "AND t.parentTask IS NULL AND t.deleted = false " +
            "AND (p IS NULL OR p.deleted = false)")
    List<Task> findActiveRootTasksForCalendar(@Param("userId") Long userId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    // -------------------------------------------------------------------------
    // Subtareas
    // -------------------------------------------------------------------------

    /**
     * Devuelve los identificadores de todas las subtareas de las tareas raíz de un proyecto.
     * Recorre la relación parentTask → project en una sola consulta JPQL para evitar N+1.
     * Se usa para cargar el historial de actividad de subtareas de un proyecto de forma eficiente.
     *
     * @param projectId identificador del proyecto
     * @return lista de IDs de subtareas pertenecientes al proyecto
     */
    @Query("SELECT t.id FROM Task t WHERE t.parentTask.id IN " +
            "(SELECT pt.id FROM Task pt WHERE pt.project.id = :projectId)")
    List<Long> findSubtaskIdsByProjectId(@Param("projectId") Long projectId);

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
     * Comprueba si una tarea padre tiene subtareas activas con un estado distinto a los indicados.
     * Se usa en {@code TaskService.updateTask} y {@code TaskService.changeStatus}
     * (vía {@code hasPendingSubtasks}) para validar que todas las subtareas
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
     * Cuenta las tareas activas de un usuario: no eliminadas y que no pertenezcan
     * a un proyecto en la papelera.
     *
     * <p>El {@code LEFT JOIN} conserva las tareas personales, que no tienen proyecto.</p>
     *
     * @param userId identificador del usuario
     * @return número de tareas activas
     */
    @Query("SELECT COUNT(t) FROM Task t LEFT JOIN t.project p " +
            "WHERE t.user.id = :userId AND t.deleted = false " +
            "AND (p IS NULL OR p.deleted = false)")
    long countActiveTasks(@Param("userId") Long userId);

    /**
     * Cuenta las tareas activas de un usuario con un estado concreto, excluyendo
     * las de proyectos en la papelera.
     * Llamado múltiples veces desde {@code UserService.getStats}
     * para cada estado (TODO, IN_PROGRESS, DONE, CANCELLED).
     *
     * <p>El {@code LEFT JOIN} conserva las tareas personales, que no tienen proyecto.</p>
     *
     * @param userId identificador del usuario
     * @param status estado por el que filtrar
     * @return número de tareas activas con ese estado
     */
    @Query("SELECT COUNT(t) FROM Task t LEFT JOIN t.project p " +
            "WHERE t.user.id = :userId AND t.status = :status AND t.deleted = false " +
            "AND (p IS NULL OR p.deleted = false)")
    long countActiveTasksByStatus(@Param("userId") Long userId,
                                  @Param("status") TaskStatus status);

    // -------------------------------------------------------------------------
    // Limpieza tras el borrado físico de tareas
    // -------------------------------------------------------------------------

    /**
     * Borra las filas de {@code task_dependencies} que no pueden ser válidas: las que
     * apuntan a una tarea inexistente (por cualquiera de sus dos columnas) y las que
     * enlazan tareas de usuarios distintos, que {@code TaskService.addDependency} nunca crea.
     *
     * <p>Existe porque {@code Task.dependents} es el lado inverso de la relación y
     * Hibernate no borra sus filas al eliminar la tarea, el esquema SQLite no tiene
     * claves foráneas (ROB-3) y los ids de {@code tasks} se reutilizan: sin esta
     * limpieza, la siguiente tarea creada por cualquier usuario heredaría la fila (SEG-8).</p>
     *
     * <p>Es SQL nativo porque {@code task_dependencies} no es una entidad y JPQL no
     * puede borrar en ella. {@code flushAutomatically} garantiza que los {@code DELETE}
     * de tareas pendientes se envían antes, para que la consulta los vea. Es idempotente.</p>
     *
     * @return número de filas borradas
     */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value =
            "DELETE FROM task_dependencies " +
            "WHERE task_id NOT IN (SELECT id FROM tasks) " +
            "OR depends_on_task_id NOT IN (SELECT id FROM tasks) " +
            "OR EXISTS (SELECT 1 FROM tasks t JOIN tasks p ON p.id = task_dependencies.depends_on_task_id " +
            "WHERE t.id = task_dependencies.task_id AND t.user_id <> p.user_id)")
    int deleteInvalidDependencies();

    /**
     * Borra los registros de trabajo cuya tarea ya no existe.
     *
     * <p>{@code Task} no tiene colección de registros (no hay cascada), el esquema SQLite
     * no tiene claves foráneas (ROB-3) y los ids de {@code tasks} se reutilizan: sin esta
     * limpieza, la siguiente tarea creada por cualquier usuario heredaría los registros (SEG-9).</p>
     *
     * <p>Es SQL nativo por coherencia con {@link #deleteInvalidDependencies()} y para no depender
     * del modelo; {@code flushAutomatically} por el mismo motivo. Es idempotente. No detecta los
     * registros ya reasignados a una tarea viva, porque no hay forma fiable de distinguirlos.</p>
     *
     * @return número de filas borradas
     */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value =
            "DELETE FROM work_logs WHERE task_id NOT IN (SELECT id FROM tasks)")
    int deleteOrphanWorkLogs();

    /**
     * Único punto común que hay que llamar, en la misma transacción y justo después de cada
     * borrado físico de tareas o proyectos: ejecuta {@link #deleteInvalidDependencies()}
     * (SEG-8) y {@link #deleteOrphanWorkLogs()} (SEG-9).
     *
     * <p>{@code @Transactional} explícito para que, fuera de un servicio, no herede el
     * {@code readOnly} de {@code SimpleJpaRepository}; dentro de un servicio se une a su
     * transacción.</p>
     */
    @Transactional
    default void deleteOrphanTaskReferences() {
        deleteInvalidDependencies();
        deleteOrphanWorkLogs();
    }
}
