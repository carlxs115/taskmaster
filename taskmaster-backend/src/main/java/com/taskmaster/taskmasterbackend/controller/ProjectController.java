package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.response.ProjectResponse;
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
 * solo puede acceder y modificar sus propios proyectos, ya que el
 * {@code userId} se extrae siempre del token de autenticación.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final SecurityUtils securityUtils;

    // -------------------------------------------------------------------------
    // Lectura
    // -------------------------------------------------------------------------

    /**
     * GET /api/projects
     * Devuelve todos los proyectos activos (no eliminados) del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con la lista de proyectos activos
     */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(
                projectService.getProjectByUser(userId)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    /**
     * GET /api/projects/trash
     * Devuelve los proyectos en la papelera del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con la lista de proyectos eliminados
     */
    @GetMapping("/trash")
    public ResponseEntity<List<ProjectResponse>> getDeletedProjects(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(
                projectService.getDeletedProjectsByUser(userId)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    /**
     * GET /api/projects/{id}
     * Devuelve un proyecto concreto del usuario autenticado.
     * Valida que el proyecto pertenece al usuario antes de devolverlo.
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el proyecto encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(
                toResponse(projectService.getProjectByIdAndUser(id, userId))
        );
    }

    // -------------------------------------------------------------------------
    // Escritura
    // -------------------------------------------------------------------------

    /**
     * POST /api/projects
     * Crea un nuevo proyecto para el usuario autenticado.
     * Si no se especifican estado o prioridad, el servicio aplica
     * los valores por defecto ({@code TODO} y {@code MEDIUM}).
     *
     * @param name        nombre del proyecto
     * @param description descripción opcional
     * @param category    categoría del proyecto
     * @param status      estado inicial (opcional, por defecto {@code TODO})
     * @param priority    prioridad inicial (opcional, por defecto {@code MEDIUM})
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 201 Created con el proyecto creado
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam TaskCategory category,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        Project project = projectService.createProject(
                name, description, category, status, priority, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(project));
    }

    /**
     * PUT /api/projects/{id}
     * Actualiza los datos de un proyecto del usuario autenticado.
     * Valida que el proyecto pertenece al usuario antes de modificarlo.
     *
     * @param id          identificador del proyecto
     * @param name        nuevo nombre
     * @param description nueva descripción (opcional)
     * @param category    nueva categoría
     * @param status      nuevo estado (opcional)
     * @param priority    nueva prioridad (opcional)
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el proyecto actualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam TaskCategory category,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        Project project = projectService.updateProject(
                id, name, description, category, status, priority, userId);
        return ResponseEntity.ok(toResponse(project));
    }

    // -------------------------------------------------------------------------
    // Papelera
    // -------------------------------------------------------------------------

    /**
     * DELETE /api/projects/{id}
     * Envía un proyecto a la papelera (soft delete).
     * El proyecto no se elimina físicamente hasta que se vacíe la papelera
     * o expire el periodo de retención configurado.
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        projectService.deleteProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/projects/{id}/restore
     * Restaura un proyecto desde la papelera, limpiando su fecha de eliminación.
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el proyecto restaurado
     */
    @PutMapping("/{id}/restore")
    public ResponseEntity<ProjectResponse> restoreProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(toResponse(projectService.restoreProject(id, userId)));
    }

    /**
     * DELETE /api/projects/{id}/permanent
     * Elimina definitivamente un proyecto de la base de datos.
     * Operación irreversible: no se puede deshacer.
     *
     * @param id          identificador del proyecto
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDeleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        projectService.deletePermanently(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/projects/trash/empty
     * Elimina permanentemente todos los proyectos en la papelera del usuario.
     * Operación irreversible: vacía la papelera completa.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
    @DeleteMapping("/trash/empty")
    public ResponseEntity<Void> emptyProjectTrash(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        projectService.emptyTrash(userId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Convierte una entidad {@link Project} al DTO de respuesta {@link ProjectResponse}.
     * Omite la referencia interna al usuario y la lista de tareas para no
     * exponer datos innecesarios ni causar referencias circulares en la serialización.
     *
     * @param project entidad proyecto a convertir
     * @return DTO con los datos del proyecto listos para serializar a JSON
     */
    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getPriority(),
                project.getCategory(),
                project.getCreatedAt(),
                project.isDeleted(),
                project.getDeletedAt()
        );
    }
}