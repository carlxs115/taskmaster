package com.taskmaster.service;

import com.taskmaster.model.*;
import com.taskmaster.model.enums.ActionType;
import com.taskmaster.model.enums.TaskCategory;
import com.taskmaster.model.enums.TaskPriority;
import com.taskmaster.model.enums.TaskStatus;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SERVICIO DE TASK
 *
 * Contiene la lógica de negocio más importante de TaskMaster,
 * incluyendo la regla: "no se puede completar una tarea si tiene subtareas pendientes".
 * Incluye soft delete - las tareas eliminadas van a la papelera única del usuario en vez de borrarse físicamente.
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
     * Tareas personales activas del usuario (sin proyecto).
     */
    public List<Task> getPersonalTasks(Long userId) {
        return taskRepository.findByUserIdAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(userId);
    }

    /**
     * Tareas raíz de un proyecto (sin tarea padre, no eliminadas).
     */
    public List<Task> getTasksByProject(Long projectId, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);
        return taskRepository.findByProjectIdAndParentTaskIsNullAndDeletedFalse(projectId);
    }

    /**
     * Subtareas activas de una tarea concreta.
     */
    public List<Task> getSubTasks(Long parentTaskId) {
        return taskRepository.findByParentTaskIdAndDeletedFalse(parentTaskId);
    }

    /**
     * Papelera del usuario: tareas de sus proyectos + tareas personales eliminadas.
     */
    public List<Task> getDeletedTasksByUser(Long userId) {
        List<Task> projectTasks  = taskRepository.findByProjectUserIdAndDeletedTrue(userId);
        List<Task> personalTasks = taskRepository.findByUserIdAndProjectIsNullAndDeletedTrue(userId);
        List<Task> all = new ArrayList<>(projectTasks);
        all.addAll(personalTasks);
        return all;
    }

    /**
     * Filtra tareas de un proyecto por estado.
     */
    public List<Task> getTasksByStatus(Long projectId, TaskStatus status, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);
        return taskRepository.findByProjectIdAndStatusAndDeletedFalse(projectId, status);
    }

    /**
     * Filtra tareas de un proyecto por prioridad.
     */
    public List<Task> getTasksByPriority(Long projectId, TaskPriority priority, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);
        return taskRepository.findByProjectIdAndPriorityAndDeletedFalse(projectId, priority);
    }

    /**
     * Tareas activas de una categoría sin proyecto, filtradas por usuario.
     */
    public List<Task> getTasksByCategory(TaskCategory category, Long userId) {
        return taskRepository.findByUserIdAndCategoryAndProjectIsNullAndParentTaskIsNullAndDeletedFalse(userId, category);
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * Crea una nueva tarea. Siempre asigna el usuario propietario.
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
                .user(user);  // ← siempre se asigna el propietario

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

        // determinar si es tarea o subtarea
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
     * Actualiza los campos editables de una tarea.
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
     * Cambia el estado de una tarea.
     * Regla: no se puede marcar DONE si tiene subtareas pendientes.
     */
    public Task changeStatus(Long taskId, TaskStatus newStatus, Long userId) {
        Task task = findById(taskId);
        if (task.getProject() != null) {
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

        activityLogService.log(
                userId,
                ActionType.TASK_STATUS_CHANGED,
                "TASK",
                saved.getId(),
                saved.getTitle(),
                oldStatus.name(),
                newStatus.name()
        );
        return saved;
    }

    /**
     * Soft delete: envía la tarea y sus subtareas a la papelera.
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

    private void softDeleteRecursive(Task task) {
        task.setDeleted(true);
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.save(task);
        List<Task> subTasks = taskRepository.findByParentTaskIdAndDeletedFalse(task.getId());
        for (Task sub : subTasks) softDeleteRecursive(sub);
    }

    /**
     * Restaura una tarea de la papelera.
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
     * Vaciado automático: borra físicamente las tareas expiradas.
     */
    public void purgeExpiredTasks(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Task> expired = taskRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);
        taskRepository.deleteAll(expired);
    }

    public Task findById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada con id: " + taskId));
    }

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
