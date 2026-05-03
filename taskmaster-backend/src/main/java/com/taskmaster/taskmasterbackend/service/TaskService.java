package com.taskmaster.taskmasterbackend.service;

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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio que gestiona la lógica de negocio de las tareas.
 *
 * <p>Implementa el ciclo de vida completo de una tarea: creación, edición,
 * cambio de estado, borrado lógico (soft delete), restauración y eliminación
 * definitiva. Gestiona también las subtareas de forma recursiva.</p>
 *
 * <p>Regla de negocio principal: no se puede marcar una tarea como completada
 * si tiene subtareas pendientes.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Devuelve las tareas personales raíz activas de un usuario (sin proyecto ni tarea padre).
     *
     * @param userId identificador del usuario
     * @return lista de tareas personales activas
     */
    public List<Task> getPersonalTasks(Long userId) {
        return taskRepository.findByUserIdAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(userId);
    }

    /**
     * Devuelve las tareas raíz activas de un proyecto, validando que pertenece al usuario.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario
     * @return lista de tareas raíz activas del proyecto
     */
    public List<Task> getTasksByProject(Long projectId, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);
        return taskRepository.findByProjectIdAndParentTaskIsNullAndDeletedFalse(projectId);
    }

    /**
     * Devuelve las subtareas activas de una tarea.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de subtareas activas
     */
    public List<Task> getSubTasks(Long parentTaskId) {
        return taskRepository.findByParentTaskIdAndDeletedFalse(parentTaskId);
    }

    /**
     * Devuelve todas las tareas en la papelera del usuario,
     * tanto tareas de proyectos como tareas personales.
     *
     * @param userId identificador del usuario
     * @return lista combinada de tareas eliminadas
     */
    public List<Task> getDeletedTasksByUser(Long userId) {
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

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * Crea una nueva tarea o subtarea y registra el evento en el historial.
     * Si pertenece a un proyecto, hereda su categoría automáticamente.
     *
     * @param title       título de la tarea
     * @param description descripción opcional
     * @param priority    prioridad; si es {@code null} se usa {@code MEDIUM}
     * @param dueDate     fecha límite opcional
     * @param projectId   proyecto al que pertenece; {@code null} si es tarea personal
     * @param parentTaskId tarea padre; {@code null} si es tarea raíz
     * @param category    categoría; ignorada si pertenece a un proyecto
     * @param userId      identificador del usuario propietario
     * @return tarea creada y persistida
     */
    public Task createTask(String title, String description, TaskPriority priority,
                           LocalDate dueDate, Long projectId, Long parentTaskId,
                           TaskCategory category, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Task.TaskBuilder builder = Task.builder()
                .title(title)
                .description(description)
                .priority(priority != null ? priority : TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .dueDate(dueDate)
                .deleted(false)
                .user(user);

        if (projectId != null) {
            Project project = projectService.getProjectByIdAndUser(projectId, userId);
            builder.project(project);
            builder.category(project.getCategory());
        } else {
            builder.category(category != null ? category : TaskCategory.PERSONAL);
        }

        if (parentTaskId != null) {
            Task parentTask = findById(parentTaskId);
            builder.parentTask(parentTask);
        }

        Task saved = taskRepository.save(builder.build());

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
     * Actualiza los campos editables de una tarea y registra el primer cambio relevante detectado.
     *
     * @param taskId      identificador de la tarea
     * @param title       nuevo título
     * @param description nueva descripción
     * @param status      nuevo estado
     * @param priority    nueva prioridad
     * @param dueDate     nueva fecha límite
     * @param userId      identificador del usuario
     * @return tarea actualizada
     */
    public Task updateTask(Long taskId, String title, String description, TaskStatus status,
                           TaskPriority priority, LocalDate dueDate, Long userId) {
        Task task = findById(taskId);
        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }

        String oldTitle = task.getTitle();
        TaskStatus oldStatus   = task.getStatus();
        TaskPriority oldPriority = task.getPriority();
        LocalDate oldDueDate = task.getDueDate();

        if (status == TaskStatus.DONE && oldStatus != TaskStatus.DONE) {
            boolean hasPending = taskRepository
                    .existsByParentTaskIdAndStatusNotInAndDeletedFalse(
                            taskId, List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
            if (hasPending) {
                throw new RuntimeException("No puedes completar esta tarea porque tiene subtareas pendientes");
            }
        }

        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(status);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        Task saved = taskRepository.save(task);

        ActionType actionType = task.getParentTask() != null ? ActionType.SUBTASK_EDITED : ActionType.TASK_EDITED;
        String entityType = task.getParentTask() != null ? "SUBTASK" : "TASK";

        // Registrar solo el primer cambio relevante detectado
        if (!oldTitle.equals(title)) {
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldTitle, title);
        } else if (oldStatus != status) {
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldStatus.name(), status.name());
        } else if (oldPriority != priority) {
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldPriority.name(), priority.name());
        } else if (!java.util.Objects.equals(oldDueDate, dueDate)) {
            String oldStr = oldDueDate != null ? oldDueDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Sin fecha";
            String newStr = dueDate    != null ? dueDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))    : "Sin fecha";
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle(), oldStr, newStr);
        } else {
            // Cambió descripción, fecha u otro campo sin valor old/new relevante
            activityLogService.log(userId, actionType, entityType, saved.getId(), saved.getTitle());
        }

        return saved;
    }

    /**
     * Cambia el estado de una tarea y registra el evento en el historial.
     * No permite marcar una tarea como completada si tiene subtareas pendientes.
     *
     * @param taskId    identificador de la tarea
     * @param newStatus nuevo estado
     * @param userId    identificador del usuario
     * @return tarea actualizada
     * @throws RuntimeException si se intenta completar una tarea con subtareas pendientes
     */
    public Task changeStatus(Long taskId, TaskStatus newStatus, Long userId) {
        Task task = findById(taskId);
        if (task.getProject() != null && task.getParentTask() == null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }

        if (newStatus == TaskStatus.DONE) {
            boolean hasPending = taskRepository
                    .existsByParentTaskIdAndStatusNotAndDeletedFalse(taskId, TaskStatus.DONE);
            if (hasPending) {
                throw new RuntimeException("No puedes completar esta tarea porque tiene subtareas pendientes");
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
    public void deleteTask(Long taskId, Long userId) {
        Task task = findById(taskId);
        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }
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
     * Marca una tarea y todas sus subtareas como eliminadas de forma recursiva.
     *
     * @param task tarea a eliminar
     */
    private void softDeleteRecursive(Task task) {
        task.setDeleted(true);
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.save(task);
        List<Task> subTasks = taskRepository.findByParentTaskIdAndDeletedFalse(task.getId());
        for (Task sub : subTasks) softDeleteRecursive(sub);
    }

    /**
     * Restaura una tarea desde la papelera.
     *
     * @param taskId identificador de la tarea
     * @param userId identificador del usuario
     * @return tarea restaurada
     * @throws RuntimeException si la tarea no se encuentra en la papelera
     */
    public Task restoreTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .filter(t -> t.getUser().getId().equals(userId) && t.isDeleted())
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada en la papelera"));
        task.setDeleted(false);
        task.setDeletedAt(null);

        Task saved = taskRepository.save(task);
        activityLogService.log(
                userId,
                ActionType.TASK_RESTORED,
                "TASK",
                saved.getId(),
                saved.getTitle()
        );
        return saved;
    }

    /**
     * Elimina físicamente las tareas en papelera cuya antigüedad supera el periodo de retención.
     *
     * @param retentionDays días de retención configurados en {@link com.taskmaster.taskmasterbackend.model.UserSettings}
     */
    public void purgeExpiredTasks(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Task> expired = taskRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);
        taskRepository.deleteAll(expired);
    }

    /**
     * Busca una tarea por su identificador.
     *
     * @param taskId identificador de la tarea
     * @return tarea encontrada
     * @throws RuntimeException si no existe ninguna tarea con ese identificador
     */
    public Task findById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada con id: " + taskId));
    }

    /**
     * Devuelve todas las subtareas de una tarea, incluidas las eliminadas.
     *
     * @param parentTaskId identificador de la tarea padre
     * @return lista de todas las subtareas
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

    /**
     * Elimina definitivamente una tarea de la base de datos y registra el evento.
     *
     * @param taskId identificador de la tarea
     * @param userId identificador del usuario
     */
    public void deletePermanently(Long taskId, Long userId) {
        Task task = findById(taskId);
        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }
        String title = task.getTitle(); // capturar antes de borrar
        taskRepository.delete(task);
        activityLogService.log(
                userId,
                ActionType.TASK_PERMANENTLY_DELETED,
                "TASK",
                taskId,
                title
        );
    }
}
