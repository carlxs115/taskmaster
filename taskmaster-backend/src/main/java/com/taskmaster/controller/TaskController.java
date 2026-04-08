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

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final SecurityUtils securityUtils;

    // ── Home ──────────────────────────────────────────────────────────────────

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

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasksByProject(
            @RequestParam Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByProject(projectId, userId)));
    }

    @GetMapping("/{id}/subtasks")
    public ResponseEntity<List<TaskResponse>> getSubTasks(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(toResponseList(taskService.getSubTasks(id)));
    }

    @GetMapping("/trash")
    public ResponseEntity<List<TaskResponse>> getDeletedTasks(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getDeletedTasksByUser(userId)));
    }

    @GetMapping("/filter/status")
    public ResponseEntity<List<TaskResponse>> getTasksByStatus(
            @RequestParam Long projectId,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByStatus(projectId, status, userId)));
    }

    @GetMapping("/filter/priority")
    public ResponseEntity<List<TaskResponse>> getTasksByPriority(
            @RequestParam Long projectId,
            @RequestParam TaskPriority priority,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByPriority(projectId, priority, userId)));
    }

    @GetMapping("/personal")
    public ResponseEntity<List<TaskResponse>> getPersonalTasks(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getPersonalTasks(userId)));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<TaskResponse>> getTasksByCategory(
            @PathVariable TaskCategory category,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponseList(taskService.getTasksByCategory(category, userId)));
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

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

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.changeStatus(id, status, userId);
        return ResponseEntity.ok(toResponse(task));
    }

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

    @PutMapping("/{id}/restore")
    public ResponseEntity<TaskResponse> restoreTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        Task task = taskService.restoreTask(id, userId);
        return ResponseEntity.ok(toResponse(task));
    }

    // ── Mapeo ─────────────────────────────────────────────────────────────────

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

    private List<TaskResponse> toResponseList(List<Task> tasks) {
        return tasks.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
