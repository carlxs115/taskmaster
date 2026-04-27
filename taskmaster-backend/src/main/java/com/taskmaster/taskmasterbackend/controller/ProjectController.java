package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST que gestiona el CRUD de proyectos y su papelera.
 *
 * <p>Todos los endpoints requieren autenticación. El usuario autenticado
 * solo puede acceder y modificar sus propios proyectos.</p>
 *
 * @author Carlos
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
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de proyectos activos
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
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de proyectos eliminados
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
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return proyecto encontrado
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
     *
     * @param name        nombre del proyecto
     * @param description descripción opcional
     * @param category    categoría del proyecto
     * @param status      estado inicial opcional
     * @param priority    prioridad inicial opcional
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return proyecto creado con código 201 Created
     */
    @PostMapping
    public ResponseEntity<Project> createProject(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam TaskCategory category,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        Project project = projectService.createProject(name, description, category, status, priority, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    /**
     * PUT /api/projects/{id}
     * Actualiza los datos de un proyecto del usuario autenticado.
     *
     * @param id          identificador del proyecto
     * @param name        nuevo nombre
     * @param description nueva descripción opcional
     * @param category    nueva categoría
     * @param status      nuevo estado opcional
     * @param priority    nueva prioridad opcional
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return proyecto actualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam TaskCategory category,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        Project project = projectService.updateProject(id, name, description, category, status, priority, userId);
        return ResponseEntity.ok(project);
    }

    /**
     * DELETE /api/projects/{id}
     * Envía un proyecto a la papelera (soft delete).
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
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
     * Restaura un proyecto desde la papelera.
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return proyecto restaurado
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<Project> restoreProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(projectService.restoreProject(id, userId));
    }

    /**
     * DELETE /api/projects/{id}/permanent
     * Elimina definitivamente un proyecto de la base de datos.
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDeleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserId(userDetails);
        projectService.deletePermanently(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extrae el identificador del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return identificador del usuario
     */
    private Long getUserId(UserDetails userDetails) {
        return securityUtils.getUserId(userDetails);
    }
}
