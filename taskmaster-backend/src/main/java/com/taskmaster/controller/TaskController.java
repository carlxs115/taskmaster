package com.taskmaster.controller;

import com.taskmaster.dto.request.TaskRequest;
import com.taskmaster.dto.response.HomeResponse;
import com.taskmaster.dto.response.ProjectWithTasksResponse;
import com.taskmaster.dto.response.TaskResponse;
import com.taskmaster.model.*;
import com.taskmaster.security.SecurityUtils;
import com.taskmaster.service.ProjectService;
import com.taskmaster.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TASKCONTROLLER
 *
 * Gestiona el CRUD de tareas, subtareas, filtros y papelera.
 * Todos los endpoints requieren autenticación.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final SecurityUtils securityUtils;

    @GetMapping("/home")
    public ResponseEntity<HomeResponse> getHome(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);

        // Proyectos con sus tareas
        List<Project> projects = projectService.getProjectByUser(userId);
        List<ProjectWithTasksResponse> projectsWithTasks = projects.stream()
                .map(p -> ProjectWithTasksResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .category(p.getCategory().name())
                        .tasks(taskService.getTasksByProject(p.getId(), userId)
                                .stream().map(this::toResponse).toList())
                        .build())
                .toList();

        // Tareas sueltas por categoría
        List<TaskResponse> personalTasks = taskService
                .getTasksByCategory(TaskCategory.PERSONAL)
                .stream().map(this::toResponse).toList();

        List<TaskResponse> estudiosTasks = taskService
                .getTasksByCategory(com.taskmaster.model.TaskCategory.ESTUDIOS)
                .stream().map(this::toResponse).toList();

        List<TaskResponse> trabajoTasks = taskService
                .getTasksByCategory(com.taskmaster.model.TaskCategory.TRABAJO)
                .stream().map(this::toResponse).toList();

        HomeResponse homeResponse = HomeResponse.builder()
                .projects(projectsWithTasks)
                .personalTasks(personalTasks)
                .estudiosTasks(estudiosTasks)
                .trabajoTasks(trabajoTasks)
                .build();

        return ResponseEntity.ok(homeResponse);
    }

    /**
     * GET /api/tasks?projectId={id}
     * Devuelve las tareas raíz activas de un proyecto.
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasksByProject(
            @RequestParam Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        List<Task> tasks = taskService.getTasksByProject(projectId, userId);
        return ResponseEntity.ok(toResponseList(tasks));
    }

    /**
     * GET /api/tasks/{id}/subtasks
     * Devuelve las subtareas activas de una tarea concreta.
     */
    @GetMapping("/{id}/subtasks")
    public ResponseEntity<List<TaskResponse>> getSubTasks(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<Task> subtasks = taskService.getSubTasks(id);
        return ResponseEntity.ok(toResponseList(subtasks));
    }

    /**
     * GET /api/tasks/trash
     * Devuelve todas las tareas en la papelera del usuario autenticado.
     */
    @GetMapping("/trash")
    public ResponseEntity<List<TaskResponse>> getDeletedTasks(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        List<Task> tasks = taskService.getDeletedTasksByUser(userId);
        return ResponseEntity.ok(toResponseList(tasks));
    }

    /**
     * GET /api/tasks/filter?projectId={id}&status={status}
     * Filtra tareas activas de un proyecto por estado.
     */
    @GetMapping("/filter/status")
    public ResponseEntity<List<TaskResponse>> getTasksByStatus(
            @RequestParam Long projectId,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        List<Task> tasks = taskService.getTasksByStatus(projectId, status, userId);
        return ResponseEntity.ok(toResponseList(tasks));
    }

    /**
     * GET /api/tasks/filter/priority?projectId={id}&priority={priority}
     * Filtra tareas activas de un proyecto por prioridad.
     */
    @GetMapping("/filter/priority")
    public ResponseEntity<List<TaskResponse>> getTasksByPriority(
            @RequestParam Long projectId,
            @RequestParam TaskPriority priority,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        List<Task> tasks = taskService.getTasksByPriority(projectId, priority, userId);
        return ResponseEntity.ok(toResponseList(tasks));
    }

    /**
     * GET /api/tasks/personal
     * Devuelve las tareas personales del usuario (sin proyecto).
     */
    @GetMapping("/personal")
    public ResponseEntity<List<TaskResponse>> getPersonalTasks(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<Task> tasks = taskService.getPersonalTasks();
        return ResponseEntity.ok(toResponseList(tasks));
    }

    /**
     * GET /api/tasks/category/{category}
     * Devuelve tareas sin proyecto de una categoría concreta.
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<TaskResponse>> getTasksByCategory(
            @PathVariable TaskCategory category,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<Task> tasks = taskService.getTasksByCategory(category);
        return ResponseEntity.ok(toResponseList(tasks));
    }

    /**
     * POST /api/tasks
     * Crea una nueva tarea o subtarea.
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.createTask(
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getDueDate(),
                request.getProjectId(),
                request.getParentTaskId(),
                request.getCategory(),
                userId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(task));
    }

    /**
     * PUT /api/tasks/{id}
     * Actualiza los datos de una tarea.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.updateTask(
                id,
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getDueDate(),
                userId
        );
        return ResponseEntity.ok(toResponse(task));
    }

    /**
     * PATCH /api/tasks/{id}/status
     * Cambia el estado de una tarea.
     *
     * Usamos PATCH en vez de PUT porque solo actualizamos un campo,
     * no el recurso completo.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.changeStatus(id, status, userId);
        return ResponseEntity.ok(toResponse(task));
    }

    /**
     * DELETE /api/tasks/{id}
     * Envía una tarea y sus subtareas a la papelera (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        taskService.deleteTask(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDeleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        taskService.deletePermanently(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/tasks/{id}/restore
     * Restaura una tarea de la papelera.
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<TaskResponse> restoreTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.restoreTask(id, userId);
        return ResponseEntity.ok(toResponse(task));
    }

    /**
     * Convierte una entidad Task a su DTO de respuesta.
     * Evita exponer campos internos de la entidad al frontend.
     */
    private TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .deleted(task.isDeleted())
                .deletedAt(task.getDeletedAt())
                .category(task.getCategory())
                .build();
    }

    /**
     * Convierte una lista de Tasks a una lista de TaskResponse.
     * Usamos streams para transformar cada elemento de la lista.
     */
    private List<TaskResponse> toResponseList(List<Task> tasks) {
        return tasks.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
