package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.CalendarTaskDTO;
import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio que gestiona la lógica de negocio de las tareas.
 *
 * <p>Implementa el ciclo de vida completo de una tarea: creación, edición,
 * cambio de estado, borrado lógico (soft delete), restauración y eliminación
 * definitiva. Gestiona también las subtareas de forma recursiva.</p>
 *
 * <p><b>Regla de negocio principal:</b> no se puede marcar una tarea como
 * completada ({@code DONE}) si tiene subtareas pendientes.</p>
 *
 * @author Carlos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    // Formato de fecha para mostrar en el historial de actividad
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // -------------------------------------------------------------------------
    // Lectura
    // -------------------------------------------------------------------------

    /**
     * Devuelve las tareas personales raíz activas de un usuario
     * (sin proyecto ni tarea padre).
     *
     * @param userId identificador del usuario
     * @return lista de tareas personales activas
     */
    public List<Task> getPersonalTasks(Long userId) {
        return taskRepository.findByUserIdAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(userId);
    }

    /**
     * Devuelve las tareas raíz activas de un proyecto,
     * validando previamente que el proyecto pertenece al usuario.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario
     * @return lista de tareas raíz activas del proyecto
     */
    public List<Task> getTasksByProject(Long projectId, Long userId) {

        // Validamos que el proyecto existe y pertenece al usuario antes de devolver sus tareas
        projectService.getProjectByIdAndUser(projectId, userId);
        return taskRepository.findByProjectIdAndParentTaskIsNullAndDeletedFalse(projectId);
    }

    /**
     * Devuelve las subtareas activas de una tarea padre.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de subtareas activas
     */
    public List<Task> getSubTasks(Long parentTaskId) {
        return taskRepository.findByParentTaskIdAndDeletedFalse(parentTaskId);
    }

    /**
     * Devuelve todas las tareas en la papelera del usuario,
     * combinando tareas de proyectos y tareas personales.
     *
     * @param userId identificador del usuario
     * @return lista combinada de todas las tareas eliminadas
     */
    public List<Task> getDeletedTasksByUser(Long userId) {

        // Obtenemos por separado las tareas de proyectos y las personales,
        // luego las unimos en una sola lista para devolverlas juntas
        List<Task> projectTasks  = taskRepository.findByProjectUserIdAndDeletedTrue(userId);
        List<Task> personalTasks = taskRepository.findByUserIdAndProjectIsNullAndDeletedTrue(userId);

        List<Task> all = new ArrayList<>(projectTasks);
        all.addAll(personalTasks);
        return all;
    }

    /**
     * Devuelve las tareas activas de un proyecto filtradas por estado.
     *
     * @param projectId identificador del proyecto
     * @param status    estado por el que filtrar
     * @param userId    identificador del usuario
     * @return lista de tareas activas con ese estado
     */
    public List<Task> getTasksByStatus(Long projectId, TaskStatus status, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);
        return taskRepository.findByProjectIdAndStatusAndDeletedFalse(projectId, status);
    }

    /**
     * Devuelve las tareas activas de un proyecto filtradas por prioridad.
     *
     * @param projectId identificador del proyecto
     * @param priority  prioridad por la que filtrar
     * @param userId    identificador del usuario
     * @return lista de tareas activas con esa prioridad
     */
    public List<Task> getTasksByPriority(Long projectId, TaskPriority priority, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);
        return taskRepository.findByProjectIdAndPriorityAndDeletedFalse(projectId, priority);
    }

    /**
     * Devuelve las tareas personales raíz activas de un usuario filtradas por categoría.
     *
     * @param category categoría por la que filtrar
     * @param userId   identificador del usuario
     * @return lista de tareas activas con esa categoría
     */
    public List<Task> getTasksByCategory(TaskCategory category, Long userId) {
        return taskRepository.findByUserIdAndCategoryAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(userId, category);
    }

    /**
     * Devuelve las tareas activas del usuario con {@code dueDate} en el mes indicado.
     * Solo incluye tareas raíz (excluye subtareas) y tareas no eliminadas.
     *
     * @param userId identificador del usuario
     * @param year   año del mes a consultar
     * @param month  mes a consultar (1-12)
     * @return lista de DTOs con los datos necesarios para el calendario
     */
    public List<CalendarTaskDTO> getTasksForCalendar(Long userId, int year, int month) {

        // Calculamos el primer y último día del mes para acotar la consulta
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate   = startDate.withDayOfMonth(startDate.lengthOfMonth());

        return taskRepository
                .findByUserIdAndDueDateBetweenAndParentTaskIsNullAndDeletedFalse(userId, startDate, endDate)
                .stream()
                // Convertimos cada Task a un DTO ligero con solo los campos del calendario
                .map(t -> new CalendarTaskDTO(
                        t.getId(),
                        t.getTitle(),
                        t.getDueDate(),
                        t.getStatus(),
                        t.getPriority()
                ))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Escritura
    // -------------------------------------------------------------------------

    /**
     * Crea una nueva tarea o subtarea y registra el evento en el historial.
     *
     * <p>Si la tarea pertenece a un proyecto, hereda automáticamente
     * la categoría de dicho proyecto.</p>
     *
     * @param title        título de la tarea
     * @param description  descripción opcional
     * @param priority     prioridad; si es {@code null} se usa {@code MEDIUM}
     * @param dueDate      fecha límite opcional
     * @param projectId    proyecto al que pertenece; {@code null} si es tarea personal
     * @param parentTaskId tarea padre; {@code null} si es tarea raíz
     * @param category     categoría; ignorada si la tarea pertenece a un proyecto
     * @param userId       identificador del usuario propietario
     * @return tarea creada y persistida
     */
    @Transactional
    public Task createTask(String title, String description, TaskPriority priority,
                           LocalDate dueDate, Long projectId, Long parentTaskId,
                           TaskCategory category, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Construimos la tarea con los campos comunes
        Task.TaskBuilder builder = Task.builder()
                .title(title)
                .description(description)
                .priority(priority != null ? priority : TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .dueDate(dueDate)
                .deleted(false)
                .user(user);

        if (projectId != null) {
            // Tarea de proyecto: validamos pertenencia y heredamos la categoría del proyecto
            Project project = projectService.getProjectByIdAndUser(projectId, userId);
            builder.project(project);
            builder.category(project.getCategory()); // la categoría viene del proyecto
        } else {
            // Tarea personal: usamos la categoría indicada o PERSONAL por defecto
            builder.category(category != null ? category : TaskCategory.PERSONAL);
        }

        if (parentTaskId != null) {
            // Subtarea: enlazamos con la tarea padre
            Task parentTask = findById(parentTaskId);
            builder.parentTask(parentTask);
        }

        Task saved = taskRepository.save(builder.build());

        // Registramos en el historial distinguiendo entre tarea y subtarea
        boolean isSubtask = parentTaskId != null;
        activityLogService.log(
                userId,
                isSubtask ? ActionType.SUBTASK_CREATED : ActionType.TASK_CREATED,
                isSubtask ? "SUBTASK" : "TASK",
                saved.getId(),
                saved.getTitle()
        );
        return saved;
    }

    /**
     * Actualiza los campos editables de una tarea y registra en el historial
     * el primer cambio relevante detectado.
     *
     * <p>Orden de detección de cambios: título → estado → prioridad → fecha límite → otros.</p>
     *
     * @param taskId      identificador de la tarea
     * @param title       nuevo título
     * @param description nueva descripción
     * @param status      nuevo estado
     * @param priority    nueva prioridad
     * @param dueDate     nueva fecha límite
     * @param userId      identificador del usuario
     * @return tarea actualizada
     * @throws BusinessException si se intenta completar una tarea con subtareas pendientes
     */
    @Transactional
    public Task updateTask(Long taskId, String title, String description, TaskStatus status,
                           TaskPriority priority, LocalDate dueDate, Long userId) {

        Task task = findById(taskId);

        // Si la tarea pertenece a un proyecto, validamos que el proyecto es del usuario
        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }

        // Guardamos los valores anteriores para detectar qué cambió y registrarlo
        String oldTitle = task.getTitle();
        TaskStatus oldStatus   = task.getStatus();
        TaskPriority oldPriority = task.getPriority();
        LocalDate oldDueDate = task.getDueDate();

        // Regla de negocio: no se puede completar una tarea con subtareas pendientes
        if (status == TaskStatus.DONE && oldStatus != TaskStatus.DONE) {
            boolean hasPending = taskRepository
                    .existsByParentTaskIdAndStatusNotInAndDeletedFalse(
                            taskId, List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
            if (hasPending) {
                throw new BusinessException("No puedes completar esta tarea porque tiene subtareas pendientes");
            }
        }

        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(status);
        task.setPriority(priority);
        task.setDueDate(dueDate);

        Task saved = taskRepository.save(task);

        // Determinamos el tipo de entidad para el log (tarea o subtarea)
        ActionType actionType = task.getParentTask() != null ? ActionType.SUBTASK_EDITED : ActionType.TASK_EDITED;
        String entityType = task.getParentTask() != null ? "SUBTASK" : "TASK";

        // Registramos solo el primer cambio relevante detectado en orden de prioridad
        if (!oldTitle.equals(title)) {
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldTitle, title);
        } else if (oldStatus != status) {
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldStatus.name(), status.name());
        } else if (oldPriority != priority) {
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldPriority.name(), priority.name());
        } else if (!java.util.Objects.equals(oldDueDate, dueDate)) {
            // Formateamos las fechas para que sean legibles en el historial
            String oldStr = oldDueDate != null ? oldDueDate.format(DATE_FORMATTER) : "Sin fecha";
            String newStr = dueDate    != null ? dueDate.format(DATE_FORMATTER)    : "Sin fecha";
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldStr, newStr);
        } else {
            // Cambio en descripción u otro campo sin valor old/new relevante para mostrar
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle());
        }

        return saved;
    }

    /**
     * Cambia el estado de una tarea y registra el evento en el historial.
     *
     * <p><b>Regla de negocio:</b> no se puede marcar como {@code DONE}
     * si tiene subtareas pendientes.</p>
     *
     * @param taskId    identificador de la tarea
     * @param newStatus nuevo estado a aplicar
     * @param userId    identificador del usuario
     * @return tarea actualizada
     * @throws BusinessException si se intenta completar una tarea con subtareas pendientes
     */
    @Transactional
    public Task changeStatus(Long taskId, TaskStatus newStatus, Long userId) {
        Task task = findById(taskId);

        // Solo validamos pertenencia al proyecto para tareas raíz (no subtareas)
        if (task.getProject() != null && task.getParentTask() == null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }

        // Regla de negocio: una tarea no puede completarse si tiene subtareas pendientes
        if (newStatus == TaskStatus.DONE) {
            boolean hasPending = taskRepository
                    .existsByParentTaskIdAndStatusNotAndDeletedFalse(taskId, TaskStatus.DONE);
            if (hasPending) {
                throw new BusinessException("No puedes completar esta tarea porque tiene subtareas pendientes");
            }
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        Task saved = taskRepository.save(task);

        boolean isSubtask = task.getParentTask() != null;
        activityLogService.log(
                userId,
                ActionType.TASK_STATUS_CHANGED,
                isSubtask ? "SUBTASK" : "TASK",
                saved.getId(),
                saved.getTitle(),
                oldStatus.name(),
                newStatus.name()
        );

        return saved;
    }

    /**
     * Envía una tarea y todas sus subtareas a la papelera (soft delete recursivo).
     *
     * @param taskId identificador de la tarea
     * @param userId identificador del usuario
     */
    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        Task task = findById(taskId);

        // Si la tarea pertenece a un proyecto, validamos que es del usuario
        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }

        // Marcamos la tarea y todas sus subtareas como eliminadas recursivamente
        softDeleteRecursive(task);

        activityLogService.log(
                task.getUser().getId(),
                task.getParentTask() != null ? ActionType.SUBTASK_DELETED : ActionType.TASK_DELETED,
                task.getParentTask() != null ? "SUBTASK" : "TASK",
                task.getId(),
                task.getTitle()
        );
    }

    /**
     * Restaura una tarea desde la papelera, limpiando su fecha de eliminación.
     *
     * @param taskId identificador de la tarea
     * @param userId identificador del usuario
     * @return tarea restaurada
     * @throws ResourceNotFoundException si la tarea no se encuentra en la papelera del usuario
     */
    @Transactional
    public Task restoreTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                // Verificamos que la tarea pertenece al usuario y está en la papelera
                .filter(t -> t.getUser().getId().equals(userId) && t.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada en la papelera"));

        task.setDeleted(false);
        task.setDeletedAt(null); // limpiamos la fecha de eliminación
        Task saved = taskRepository.save(task);

        activityLogService.log(userId, ActionType.TASK_RESTORED, "TASK", saved.getId(), saved.getTitle());
        return saved;
    }

    /**
     * Elimina físicamente las tareas en papelera cuya antigüedad supera
     * el periodo de retención configurado por el usuario.
     * Llamado automáticamente por {@link com.taskmaster.taskmasterbackend.TrashScheduler}.
     *
     * @param retentionDays días de retención configurados en {@link com.taskmaster.taskmasterbackend.model.UserSettings}
     */
    @Transactional
    public void purgeExpiredTasks(Long userId, int retentionDays) {

        // Calculamos la fecha límite: tareas eliminadas antes de esta fecha se purgan
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        // Filtramos por userId además de por fecha para no afectar a otros usuarios
        List<Task> expired = taskRepository.findByUserIdAndDeletedTrueAndDeletedAtBefore(userId, cutoff);

        if (!expired.isEmpty()) {
            log.info("Purgando {} tareas expiradas del usuario {} (retención: {} días)",
                    expired.size(), userId, retentionDays);
            taskRepository.deleteAll(expired);
        }
    }

    /**
     * Elimina definitivamente una tarea de la base de datos y registra el evento.
     *
     * @param taskId identificador de la tarea
     * @param userId identificador del usuario
     */
    @Transactional
    public void deletePermanently(Long taskId, Long userId) {
        Task task = findById(taskId);

        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }

        String title = task.getTitle(); // capturamos el título antes de borrar la entidad
        taskRepository.delete(task);

        activityLogService.log(userId, ActionType.TASK_PERMANENTLY_DELETED, "TASK", taskId, title);
    }

    /**
     * Elimina definitivamente todas las tareas en la papelera del usuario.
     *
     * @param userId identificador del usuario
     */
    @Transactional
    public void emptyTrash(Long userId) {
        List<Task> deleted = getDeletedTasksByUser(userId);

        // Eliminamos todas de golpe con deleteAll en lugar de una por una
        taskRepository.deleteAll(deleted);

        // Registramos cada eliminación en el historial de actividad
        for (Task task : deleted) {
            activityLogService.log(userId, ActionType.TASK_PERMANENTLY_DELETED,
                    "TASK", task.getId(), task.getTitle());
        }
    }

    // -------------------------------------------------------------------------
    // Métodos de soporte
    // -------------------------------------------------------------------------

    /**
     * Busca una tarea por su identificador.
     *
     * @param taskId identificador de la tarea
     * @return tarea encontrada
     * @throws ResourceNotFoundException si no existe ninguna tarea con ese identificador
     */
    public Task findById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con id: " + taskId));
    }

    /**
     * Devuelve todas las subtareas de una tarea, incluidas las eliminadas.
     * Se usa para cargar el detalle completo de una tarea con su historial.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de todas las subtareas, activas y eliminadas
     */
    public List<Task> getAllSubTasks(Long parentTaskId) {
        return taskRepository.findByParentTaskId(parentTaskId);
    }

    /**
     * Devuelve todas las tareas raíz de un proyecto, incluyendo las eliminadas.
     * Se usa para cargar el historial de actividad completo del proyecto.
     *
     * @param projectId identificador del proyecto
     * @return lista de todas las tareas raíz del proyecto
     */
    public List<Task> getAllTasksByProject(Long projectId) {
        return taskRepository.findByProjectIdAndParentTaskIsNull(projectId);
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Marca una tarea y todas sus subtareas como eliminadas de forma recursiva.
     * Establece {@code deleted = true} y {@code deletedAt} con la fecha actual.
     *
     * @param task tarea raíz a eliminar (se procesará junto con toda su jerarquía)
     */
    private void softDeleteRecursive(Task task) {
        task.setDeleted(true);
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.save(task);

        // Procesamos recursivamente cada subtarea activa de esta tarea
        List<Task> subTasks = taskRepository
                .findByParentTaskIdAndDeletedFalse(task.getId());
        for (Task sub : subTasks) {
            softDeleteRecursive(sub);
        }
    }
}