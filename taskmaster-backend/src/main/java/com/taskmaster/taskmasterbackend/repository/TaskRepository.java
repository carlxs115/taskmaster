package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link Task}.
 *
 * <p>Proporciona consultas para recuperar tareas de proyecto, tareas,
 * subtareas, elementos en papelera y estadísticas de usuario.</p>
 *
 * @author Carlos
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ── Tareas de proyecto ────────────────────────────────────────────────────

    /**
     * Devuelve las tareas raíz activas de un proyecto (sin tarea padre).
     *
     * @param projectId identificador del proyecto
     * @return lista de tareas raíz activas del proyecto
     */
    List<Task> findByProjectIdAndParentTaskIsNullAndDeletedFalse(Long projectId);

    /**
     * Devuelve las subtareas activas de una tarea padre.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de subtareas activas
     */
    List<Task> findByParentTaskIdAndDeletedFalse(Long parentTaskId);

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
     * Comprueba si una tarea padre tiene subtareas activas con un estado distinto al indicado.
     * Se usa para determinar si una tarea padre puede marcarse como completada.
     *
     * @param parentTaskId identificador de la tarea padre
     * @param status       estado a excluir de la comprobación
     * @return {@code true} si existe alguna subtarea activa con estado distinto
     */
    boolean existsByParentTaskIdAndStatusNotAndDeletedFalse(Long parentTaskId, TaskStatus status);


    // ── Tareas sin proyecto ─────────────────────────────────────────────────────

    /**
     * Devuelve las tareas en papelera de un usuario.
     *
     * @param userId identificador del usuario
     * @return lista de tareas personales eliminadas
     */
    List<Task> findByUserIdAndProjectIsNullAndDeletedTrue(Long userId);

    /**
     * Devuelve las tareas raíz activas de un usuario (sin proyecto ni tarea padre).
     *
     * @param userId identificador del usuario
     * @return lista de tareas personales raíz activas
     */
    List<Task> findByUserIdAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(Long userId);

    /**
     * Devuelve las tareas raíz activas de un usuario filtradas por categoría.
     *
     * @param userId   identificador del usuario
     * @param category categoría por la que filtrar
     * @return lista de tareas personales raíz activas con esa categoría
     */
    List<Task> findByUserIdAndCategoryAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(Long userId, TaskCategory category);

    // ── Papelera ──────────────────────────────────────────────────────────────

    /**
     * Devuelve las tareas de proyectos del usuario que están en la papelera.
     *
     * @param userId identificador del usuario propietario del proyecto
     * @return lista de tareas de proyecto eliminadas
     */
    List<Task> findByProjectUserIdAndDeletedTrue(Long userId);

    /**
     * Devuelve las tareas en papelera cuya fecha de eliminación es anterior a la indicada.
     * Se usa para el vaciado automático por el scheduler.
     *
     * @param cutoffDate fecha límite
     * @return lista de tareas a eliminar definitivamente
     */
    List<Task> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoffDate);

    // ── Estadísticas ──────────────────────────────────────────────────────────

    /**
     * Cuenta las tareas activas de un usuario.
     *
     * @param userId identificador del usuario
     * @return número de tareas activas
     */
    long countByUserIdAndDeletedFalse(Long userId);

    /**
     * Cuenta las tareas activas de un usuario con un estado concreto.
     *
     * @param userId identificador del usuario
     * @param status estado por el que filtrar
     * @return número de tareas activas con ese estado
     */
    long countByUserIdAndStatusAndDeletedFalse(Long userId, TaskStatus status);

    // ── Subtareas y tareas incluyendo eliminadas ───────────────────────────────

    /**
     * Devuelve todas las subtareas de una tarea padre, incluyendo las eliminadas.
     * Se usa para cargar el historial de actividad completo de una tarea,
     * ya que las subtareas eliminadas siguen teniendo registros en el log.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de todas las subtareas, activas y eliminadas
     */
    List<Task> findByParentTaskId(Long parentTaskId);

    /**
     * Devuelve todas las tareas raíz de un proyecto, incluyendo las eliminadas.
     * Se usa para cargar el historial de actividad completo de un proyecto,
     * ya que las tareas eliminadas siguen teniendo registros en el log.
     *
     * @param projectId identificador del proyecto
     * @return lista de todas las tareas raíz del proyecto, activas y eliminadas
     */
    List<Task> findByProjectIdAndParentTaskIsNull(Long projectId);
}
