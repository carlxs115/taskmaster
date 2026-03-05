package com.taskmaster.service;

import com.taskmaster.model.Project;
import com.taskmaster.model.Task;
import com.taskmaster.model.TaskPriority;
import com.taskmaster.model.TaskStatus;
import com.taskmaster.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * SERVICIO DE TASK
 *
 * Contiene la lógica de negocio más importante de TaskMaster,
 * incluyendo la regla: "no se puede completar una tarea si tiene subtareas pendientes".
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;

    /**
     * Devuelve las tareas raíz de un proyecto (sin tarea padre).
     * Las subtareas se cargan desde cada tarea con getSubTasks().
     */
    public List<Task> getTasksByProject(Long projectId, Long userId) {
        // Validamos que el proyecto pertenece al usuario
        projectService.getProjectByIdAndUser(projectId, userId);

        return taskRepository.findByProjectIdAndParentTaskIsNull(projectId);
    }

    /**
     * Devuelve las subtareas de una tarea concreta.
     */
    public List<Task> getSubTasks(Long parentTaskId) {
        return taskRepository.findByParentTaskId(parentTaskId);
    }

    /**
     * Filtra tareas de un proyecto por estado.
     */
    public List<Task> getTasksByStatus(Long projectId, TaskStatus status, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);

        return taskRepository.findByProjectIdAndStatus(projectId, status);
    }

    /**
     * Filtra tareas de un proyecto por prioridad.
     */
    public List<Task> getTasksByPriority(Long projectId, TaskPriority priority, Long userId) {
        projectService.getProjectByIdAndUser(projectId, userId);

        return taskRepository.findByProjectIdAndPriority(projectId, priority);
    }

    /**
     * Crea una nueva tarea en un proyecto.
     * Si se proporciona parentTaskId, se crea como subtarea.
     */
    public Task createTask(String title, String description, TaskPriority priority,
                           LocalDate dueDate, Long projectId, Long parentTaskId, Long userId) {

        Project project = projectService.getProjectByIdAndUser(projectId, userId);

        Task.TaskBuilder builder = Task.builder()
                .title(title)
                .description(description)
                .priority(priority != null ? priority : TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .dueDate(dueDate)
                .project(project);

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
                           TaskPriority priority, LocalDate dueDate, Long projectId, Long userId) {

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
     */
    public Task changeStatus(Long taskId, TaskStatus newStatus, Long userId) {
        Task task = findById(taskId);
        projectService.getProjectByIdAndUser(task.getProject().getId(), userId);

        if (newStatus == TaskStatus.DONE) {
            boolean hasSubTasksPending = taskRepository
                    .existsByParentTaskIdAndStatusNot(taskId, TaskStatus.DONE);

            if (hasSubTasksPending) {
                throw new RuntimeException("No puedes completar esta tarea porque tiene subtareas pendientes");
            }
        }

        task.setStatus(newStatus);
        return taskRepository.save(task);
    }

    /**
     * Elimina una tarea. Al tener cascade = ALL, se eliminan también
     * todas sus subtareas automáticamente.
     */
    public void deleteTask(Long taskId, Long userId) {
        Task task = findById(taskId);
        projectService.getProjectByIdAndUser(task.getProject().getId(), userId);
        taskRepository.delete(task);
    }

    /**
     * Busca una tarea por id.
     */
    private Task findById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada con id: " + taskId));
    }
}
