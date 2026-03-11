package com.taskmaster.controller;

import com.taskmaster.model.Project;
import com.taskmaster.model.TaskCategory;
import com.taskmaster.security.SecurityUtils;
import com.taskmaster.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PROJECTCONTROLLER
 *
 * Gestiona el CRUD de proyectos y la papelera.
 * Todos los endpoints requieren autenticación.
 *
 * @AuthenticationPrincipal UserDetails -> Spring Security inyecta automáticamente
 * el usuario autenticado en cada petición. Así sabemos quién está haciendo
 * la petición sin necesidad de que el frontend envíe el userId manualmente.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/projects
     * Devuelve todos los proyectos activos del usuario autenticado.
     */
    @GetMapping
    public ResponseEntity<List<Project>> getProjects(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(projectService.getProjectByUser(userId));
    }

    /**
     * GET /api/projects/trash
     * Devuelve los proyectos en la papelera del usuario autenticado.
     */
    @GetMapping("/trash")
    public ResponseEntity<List<Project>> getDeletedProjects(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(projectService.getDeletedProjectsByUser(userId));
    }

    /**
     * GET /api/projects/{id}
     * Devuelve un proyecto concreto del usuario autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(projectService.getProjectByIdAndUser(id, userId));
    }

    /**
     * POST /api/projects
     * Crea un nuevo proyecto para el usuario autenticado.
     */
    @PostMapping
    public ResponseEntity<Project> createProject(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam TaskCategory category,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        Project project = projectService.createProject(name, description, category, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    /**
     * PUT /api/projects/{id}
     * Actualiza un proyecto del usuario autenticado.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam TaskCategory category,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        Project project = projectService.updateProject(id, name, description, category, userId);
        return ResponseEntity.ok(project);
    }

    /**
     * DELETE /api/projects/{id}
     * Envía un proyecto a la papelera (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        projectService.deleteProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/projects/{id}/restore
     * Restaura un proyecto de la papelera.
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<Project> restoreProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(projectService.restoreProject(id, userId));
    }

    /**
     * Obtiene el userId del usuario autenticado a partir de su username.
     *
     * NOTA: más adelante refactorizaremos esto a un método en un helper compartido entre controladores.
     */
    private Long getUserId(UserDetails userDetails) {
        return securityUtils.getUserId(userDetails);
    }
}
