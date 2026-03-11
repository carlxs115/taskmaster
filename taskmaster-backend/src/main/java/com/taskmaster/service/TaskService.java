package com.taskmaster.service;

import com.taskmaster.model.*;
import com.taskmaster.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * Devuelve las tareas personales activas del usuario (sin proyecto).
     */
    public List<Task> getPersonalTasks() {
        return taskRepository.findByProjectIsNullAndDeletedFalse();
    }

    /**
     * Devuelve las tareas raíz de un proyecto (sin tarea padre y no eliminadas).
     * Las subtareas se cargan desde cada tarea con getSubTasks().
     */
    public List<Task> getTasksByProject(Long projectId, Long userId) {
        // Validamos que el proyecto pertenece al usuario
        projectService.getProjectByIdAndUser(projectId, userId);

        return taskRepository.findByProjectIdAndParentTaskIsNullAndDeletedFalse(projectId);
    }

    /**
     * Devuelve las subtareas activas de una tarea concreta.
     */
    public List<Task> getSubTasks(Long parentTaskId) {
        return taskRepository.findByParentTaskIdAndDeletedFalse(parentTaskId);
    }

    /**
     * PAPELERA — devuelve todas las tareas eliminadas del usuario
     * independientemente del proyecto al que pertenezcan.
     */
    public List<Task> getDeletedTasksByUser(Long userId) {
        return taskRepository.findByProjectUserIdAndDeletedTrue(userId);
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
     * Tareas activas de una categoría sin proyecto.
     */
    public List<Task> getTasksByCategory(TaskCategory category) {
        return taskRepository.findByCategoryAndProjectIsNullAndDeletedFalse(category);
    }

    /**
     * Crea una nueva tarea en un proyecto.
     * Si se proporciona parentTaskId, se crea como subtarea.
     */
    public Task createTask(String title, String description, TaskPriority priority,
                           LocalDate dueDate, Long projectId, Long parentTaskId, TaskCategory category, Long userId) {

        Task.TaskBuilder builder = Task.builder()
                .title(title)
                .description(description)
                .priority(priority != null ? priority : TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .dueDate(dueDate)
                .deleted(false);

        // Si tiene proyecto lo asignamos, si no es tarea personal
        if (projectId != null) {
            Project project = projectService.getProjectByIdAndUser(projectId, userId);
            builder.project(project);
            // Hereda la categoría del proyecto automáticamente
            builder.category(project.getCategory());
        } else {
            // Tarea sin proyecto - usa la categoría indicada por el usuario
            builder.category(category != null ? category : TaskCategory.PERSONAL);
        }

        // Si tiene tarea padre, la asignamos
        if (parentTaskId != null) {
            Task parentTask = findById(parentTaskId);
            builder.parentTask(parentTask);
        }

        return taskRepository.save(builder.build());
    }

    /**
     * Actualiza los campos de una tarea existente.
     */
    public Task updateTask(Long taskId, String title, String description,
                           TaskPriority priority, LocalDate dueDate, Long userId) {

        Task task = findById(taskId);
        projectService.getProjectByIdAndUser(task.getProject().getId(), userId);

        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setDueDate(dueDate);

        return taskRepository.save(task);
    }

    /**
     * Cambia el estado de una tarea.
     *
     * REGLA DE NEGOCIO CLAVE:
     * Si se intenta marcar como DONE, comprobamos que no tenga
     * subtareas en estado TODO o IN_PROGRESS.
     *
     * existsByParentTaskIdAndStatusNot(taskId, TaskStatus.DONE)
     * devuelve true si hay alguna subtarea que NO esté en DONE,
     * es decir, que esté pendiente → lanzamos excepción.
     *
     * Las subtareas en la papelera no cuentan como pendientes.
     */
    public Task changeStatus(Long taskId, TaskStatus newStatus, Long userId) {
        Task task = findById(taskId);

        if (task.getProject() != null) {
            projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        }

        if (newStatus == TaskStatus.DONE) {
            boolean hasSubTasksPending = taskRepository
                    .existsByParentTaskIdAndStatusNotAndDeletedFalse(taskId, TaskStatus.DONE);

            if (hasSubTasksPending) {
                throw new RuntimeException("No puedes completar esta tarea porque tiene subtareas pendientes");
            }
        }

        task.setStatus(newStatus);
        return taskRepository.save(task);
    }

    /**
     * SOFT DELETE — Envía una tarea a la papelera.
     *
     * Las subtareas también se envían a la papelera en cascada
     * mediante el método recursivo softDeleteRecursive.
     */
    public void deleteTask(Long taskId, Long userId) {
        Task task = findById(taskId);
        projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        softDeleteRecursive(task);
    }

    /**
     * Marca una tarea y todas sus subtareas como eliminadas.
     * Se llama recursivamente para cubrir subtareas de subtareas ilimitadas.
     */
    private void softDeleteRecursive(Task task) {
        task.setDeleted(true);
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.save(task);

        List<Task> subTasks = taskRepository.findByParentTaskIdAndDeletedFalse(task.getId());
        for (Task subTask : subTasks) {
            softDeleteRecursive(subTask);
        }
    }

    /**
     * RESTAURAR - Saca una tarea de la papelera.
     * Solo restaura la tarea seleccionada, no sus subtareas automáticamente.
     */
    public Task restoreTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .filter(t -> t.getProject().getUser().getId().equals(userId) && t.isDeleted())
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada en la papelera"));

        task.setDeleted(false);
        task.setDeletedAt(null);
        return taskRepository.save(task);
    }

    /**
     * VACIADO AUTOMÁTICO - Borra físicamente las tareas cuya fecha
     * de eliminación supera el periodo de retención configurado.
     *
     * @param retentionDays días configurados en UserSettings (7, 15 o 30)
     */
    public void purgeExpiredTasks(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Task> expired = taskRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);
        taskRepository.deleteAll(expired);
    }

    /**
     * Busca una tarea por id.
     */
    private Task findById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada con id: " + taskId));
    }
}
