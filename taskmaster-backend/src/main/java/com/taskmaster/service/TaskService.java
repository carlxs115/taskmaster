package com.taskmaster.service;

import com.taskmaster.model.*;
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

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Tareas personales activas del usuario (sin proyecto).
     */
    public List<Task> getPersonalTasks(Long userId) {
        return taskRepository.findByUserIdAndProjectIsNullAndDeletedFalse(userId);
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
        return taskRepository.findByUserIdAndCategoryAndProjectIsNullAndDeletedFalse(userId, category);
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

        return taskRepository.save(builder.build());
    }

    /**
     * Actualiza los campos editables de una tarea.
     */
    public Task updateTask(Long taskId, String title, String description,
                           TaskPriority priority, LocalDate dueDate, Long userId) {
        Task task = findById(taskId);
        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        return taskRepository.save(task);
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
        task.setStatus(newStatus);
        return taskRepository.save(task);
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
        return taskRepository.save(task);
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
        taskRepository.delete(task);
    }
}
