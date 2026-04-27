package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.request.TaskRequest;
import com.taskmaster.taskmasterbackend.dto.response.HomeResponse;
import com.taskmaster.taskmasterbackend.dto.response.ProjectWithTasksResponse;
import com.taskmaster.taskmasterbackend.dto.response.TaskResponse;
import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.ProjectService;
import com.taskmaster.taskmasterbackend.service.TaskService;
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
 * Controlador REST que gestiona el CRUD de tareas, subtareas y la pantalla de inicio.
 *
 * <p>Todos los endpoints requieren autenticación. El usuario autenticado
 * solo puede acceder y modificar sus propias tareas.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final SecurityUtils securityUtils;

    // ── Home ──────────────────────────────────────────────────────────────────

    /**
     * GET /api/tasks/home
     * Devuelve los datos necesarios para renderizar la pantalla de inicio:
     * proyectos con sus tareas y tareas personales agrupadas por categoría.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return datos de la pantalla de inicio
     */
    @GetMapping("/home")
    public ResponseEntity<HomeResponse> getHome(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);

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

        // Ahora filtradas por userId — ya no mezcla usuarios
        List<TaskResponse> personalTasks = taskService
                .getTasksByCategory(TaskCategory.PERSONAL, userId)
                .stream().map(this::toResponse).toList();
        List<TaskResponse> estudiosTasks = taskService
                .getTasksByCategory(TaskCategory.ESTUDIOS, userId)
                .stream().map(this::toResponse).toList();
        List<TaskResponse> trabajoTasks = taskService
                .getTasksByCategory(TaskCategory.TRABAJO, userId)
                .stream().map(this::toResponse).toList();

        HomeResponse homeResponse = HomeResponse.builder()
                .projects(projectsWithTasks)
                .personalTasks(personalTasks)
                .estudiosTasks(estudiosTasks)
                .trabajoTasks(trabajoTasks)
                .build();

        return ResponseEntity.ok(homeResponse);
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * GET /api/tasks/{id}
     * Devuelve una tarea concreta por su identificador.
     *
     * @param id          identificador de la tarea
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return tarea encontrada
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.findById(id);
        return ResponseEntity.ok(toResponse(task));
    }

    /**
     * GET /api/tasks?projectId={projectId}
     * Devuelve las tareas raíz activas de un proyecto.
     *
     * @param projectId   identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de tareas del proyecto
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasksByProject(
            @RequestParam Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByProject(projectId, userId)));
    }

    /**
     * GET /api/tasks/{id}/subtasks
     * Devuelve las subtareas activas de una tarea.
     *
     * @param id          identificador de la tarea padre
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de subtareas
     */
    @GetMapping("/{id}/subtasks")
    public ResponseEntity<List<TaskResponse>> getSubTasks(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(toResponseList(taskService.getSubTasks(id)));
    }

    /**
     * GET /api/tasks/trash
     * Devuelve todas las tareas en la papelera del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de tareas eliminadas
     */
    @GetMapping("/trash")
    public ResponseEntity<List<TaskResponse>> getDeletedTasks(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getDeletedTasksByUser(userId)));
    }

    /**
     * GET /api/tasks/filter/status
     * Devuelve las tareas de un proyecto filtradas por estado.
     *
     * @param projectId   identificador del proyecto
     * @param status      estado por el que filtrar
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de tareas filtradas
     */
    @GetMapping("/filter/status")
    public ResponseEntity<List<TaskResponse>> getTasksByStatus(
            @RequestParam Long projectId,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByStatus(projectId, status, userId)));
    }

    /**
     * GET /api/tasks/filter/priority
     * Devuelve las tareas de un proyecto filtradas por prioridad.
     *
     * @param projectId   identificador del proyecto
     * @param priority    prioridad por la que filtrar
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de tareas filtradas
     */
    @GetMapping("/filter/priority")
    public ResponseEntity<List<TaskResponse>> getTasksByPriority(
            @RequestParam Long projectId,
            @RequestParam TaskPriority priority,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByPriority(projectId, priority, userId)));
    }

    /**
     * GET /api/tasks/personal
     * Devuelve las tareas personales raíz activas del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de tareas personales
     */
    @GetMapping("/personal")
    public ResponseEntity<List<TaskResponse>> getPersonalTasks(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getPersonalTasks(userId)));
    }

    /**
     * GET /api/tasks/category/{category}
     * Devuelve las tareas personales raíz activas del usuario filtradas por categoría.
     *
     * @param category    categoría por la que filtrar
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de tareas de esa categoría
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<TaskResponse>> getTasksByCategory(
            @PathVariable TaskCategory category,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByCategory(category, userId)));
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /**
     * POST /api/tasks
     * Crea una nueva tarea o subtarea para el usuario autenticado.
     *
     * @param request     datos de la tarea validados
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return tarea creada con código 201 Created
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.createTask(
                request.getTitle(), request.getDescription(), request.getPriority(),
                request.getDueDate(), request.getProjectId(), request.getParentTaskId(),
                request.getCategory(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(task));
    }

    /**
     * PUT /api/tasks/{id}
     * Actualiza los datos de una tarea del usuario autenticado.
     *
     * @param id          identificador de la tarea
     * @param request     nuevos datos de la tarea validados
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return tarea actualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.updateTask(id, request.getTitle(), request.getDescription(),
                request.getStatus(), request.getPriority(), request.getDueDate(), userId);
        return ResponseEntity.ok(toResponse(task));
    }

    /**
     * PATCH /api/tasks/{id}/status
     * Cambia el estado de una tarea.
     *
     * @param id          identificador de la tarea
     * @param status      nuevo estado
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return tarea actualizada
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
     *
     * @param id          identificador de la tarea
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        taskService.deleteTask(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/tasks/{id}/permanent
     * Elimina definitivamente una tarea de la base de datos.
     *
     * @param id          identificador de la tarea
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
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
     * Restaura una tarea desde la papelera.
     *
     * @param id          identificador de la tarea
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return tarea restaurada
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<TaskResponse> restoreTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.restoreTask(id, userId);
        return ResponseEntity.ok(toResponse(task));
    }

    // ── Mapeo ─────────────────────────────────────────────────────────────────

    /**
     * Convierte una entidad {@link Task} a su DTO de respuesta.
     *
     * @param task entidad a convertir
     * @return DTO con los datos de la tarea
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
     * Convierte una lista de entidades {@link Task} a una lista de DTOs.
     *
     * @param tasks lista de entidades a convertir
     * @return lista de DTOs
     */
    private List<TaskResponse> toResponseList(List<Task> tasks) {
        return tasks.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
