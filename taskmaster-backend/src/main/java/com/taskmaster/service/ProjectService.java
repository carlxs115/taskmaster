package com.taskmaster.service;

import com.taskmaster.model.Project;
import com.taskmaster.model.TaskCategory;
import com.taskmaster.model.User;
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
    public Project createProject(String name, String description, TaskCategory category, Long userId) {
        User user = userService.findById(userId);

        Project project = Project.builder()
                .name(name)
                .description(description)
                .category(category)
                .user(user)
                .deleted(false)
                .build();

        return projectRepository.save(project);
    }

    /**
     * Actualiza el nombre y descripción de un proyecto.
     * Valida que el proyecto pertenece al usuario antes de modificarlo.
     */
    public Project updateProject(Long projectId, String name, String description, TaskCategory category, Long userId) {
        Project project = getProjectByIdAndUser(projectId, userId);
        project.setName(name);
        project.setDescription(description);
        project.setCategory(category);

        return projectRepository.save(project);
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
    }

    /**
     * RESTAURAR - Saca un proyecto de la papelera.
     */
    public Project restoreProject(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUser().equals(userId) && p.isDeleted())
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado en la papelera"));

        project.setDeleted(false);
        project.setCreatedAt(null);
        return projectRepository.save(project);
    }

    /**
     * BORRADO FÍSICO — Elimina definitivamente un proyecto de la BD.
     * Se llama desde el vaciado automático de la papelera o si el
     * usuario decide vaciarla manualmente.
     */
    public void deletePermanently(Long projectId) {
        projectRepository.deleteById(projectId);
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
