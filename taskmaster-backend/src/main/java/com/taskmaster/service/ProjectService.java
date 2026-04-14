package com.taskmaster.service;

import com.taskmaster.model.*;
import com.taskmaster.model.enums.ActionType;
import com.taskmaster.model.enums.TaskCategory;
import com.taskmaster.model.enums.TaskPriority;
import com.taskmaster.model.enums.TaskStatus;
import com.taskmaster.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SERVICIO DE PROJECT
 *
 * Gestiona toda la lógica de negocio de proyectos.
 * Incluye soft delete — los proyectos eliminados van a la papelera
 * en vez de borrarse físicamente de la BD.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final ActivityLogService activityLogService;

    /**
     * Devuelve todos los proyectos de un usuario (no eliminados).
     */
    public List<Project> getProjectByUser(Long userId) {
        return projectRepository.findByUserIdAndDeletedFalse(userId);
    }

    /**
     * Devuelve los proyectos en la papelera de un usuario.
     */
    public List<Project> getDeletedProjectsByUser(Long userId) {
        return projectRepository.findByUserIdAndDeletedTrue(userId);
    }

    /**
     * Busca un proyecto activo por id validando que pertenece al usuario.
     *
     * @throws RuntimeException si no existe, está en la papelera o no pertenece al usuario
     */
    public Project getProjectByIdAndUser(Long projectId, Long userId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getUser().getId().equals(userId) && !p.isDeleted())
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));
    }

    /**
     * Crea un nuevo proyecto para un usuario.
     */
    public Project createProject(String name, String description,
                                 TaskCategory category, TaskStatus status,
                                 TaskPriority priority, Long userId) {
        User user = userService.findById(userId);

        Project project = Project.builder()
                .name(name)
                .description(description)
                .category(category)
                .status(status != null ? status : TaskStatus.TODO)
                .priority(priority != null ? priority : TaskPriority.MEDIUM)
                .user(user)
                .deleted(false)
                .build();

        Project saved = projectRepository.save(project);
        activityLogService.log(
                userId,
                ActionType.PROJECT_CREATED,
                "PROJECT",
                saved.getId(),
                saved.getName()
        );
        return saved;
    }

    /**
     * Actualiza el nombre y descripción de un proyecto.
     * Valida que el proyecto pertenece al usuario antes de modificarlo.
     */
    public Project updateProject(Long projectId, String name, String description,
                                 TaskCategory category, TaskStatus status,
                                 TaskPriority priority, Long userId) {
        Project project = getProjectByIdAndUser(projectId, userId);
        TaskStatus oldStatus = project.getStatus();

        project.setName(name);
        project.setDescription(description);
        project.setCategory(category);
        project.setStatus(status);
        project.setPriority(priority);

        Project saved = projectRepository.save(project);

        if (oldStatus != null && status != null && !oldStatus.equals(status)) {
            activityLogService.log(userId, ActionType.PROJECT_STATUS_CHANGED,
                    "PROJECT", saved.getId(), saved.getName(),
                    oldStatus.name(), status.name());
        } else {
            activityLogService.log(userId, ActionType.PROJECT_EDITED,
                    "PROJECT", saved.getId(), saved.getName());
        }
        return saved;
    }

    /**
     * SOFT DELETE - Envía un proyecto a la papelera.
     *
     * En vez de borrar físicamente, marcamos deleted = true
     * y guardamos la fecha de eliminación para el vaciado automático.
     */
    public void deleteProject(Long projectId, Long userId) {
        Project project = getProjectByIdAndUser(projectId, userId);
        project.setDeleted(true);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        activityLogService.log(
                userId,
                ActionType.PROJECT_DELETED,
                "PROJECT",
                projectId,
                project.getName()
        );
    }

    /**
     * RESTAURAR - Saca un proyecto de la papelera.
     */
    public Project restoreProject(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUser().getId().equals(userId) && p.isDeleted())
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado en la papelera"));

        project.setDeleted(false);
        project.setDeletedAt(null);

        Project saved = projectRepository.save(project);
        activityLogService.log(
                project.getUser().getId(),
                ActionType.PROJECT_RESTORED,
                "PROJECT",
                saved.getId(),
                saved.getName()
        );
        return saved;
    }

    /**
     * BORRADO FÍSICO — Elimina definitivamente un proyecto de la BD.
     * Se llama desde el vaciado automático de la papelera o si el
     * usuario decide vaciarla manualmente.
     */
    public void deletePermanently(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));
        String name = project.getName();
        projectRepository.deleteById(projectId);
        activityLogService.log(
                userId,
                ActionType.PROJECT_PERMANENTLY_DELETED,
                "PROJECT",
                projectId,
                name
        );
    }

    /**
     * VACIADO AUTOMÁTICO — Borra físicamente los proyectos cuya fecha
     * de eliminación supera el periodo de retención configurado por el usuario.
     *
     * @param retentionDays días configurados en UserSettings (7, 15 o 30)
     */
    public void purgeExpiredProjects(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Project> expired = projectRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);
        projectRepository.deleteAll(expired);
    }
}
