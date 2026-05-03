package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio que gestiona la lógica de negocio de los proyectos.
 *
 * <p>Implementa el ciclo de vida completo de un proyecto: creación, edición,
 * borrado lógico (soft delete), restauración desde la papelera y eliminación
 * definitiva. Todos los eventos relevantes quedan registrados en el historial
 * de actividad mediante {@link ActivityLogService}.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final ActivityLogService activityLogService;
    private final TaskRepository taskRepository;

    /**
     * Devuelve todos los proyectos activos de un usuario.
     *
     * @param userId identificador del usuario
     * @return lista de proyectos no eliminados
     */
    public List<Project> getProjectByUser(Long userId) {
        return projectRepository.findByUserIdAndDeletedFalse(userId);
    }

    /**
     * Devuelve los proyectos en la papelera de un usuario.
     *
     * @param userId identificador del usuario
     * @return lista de proyectos eliminados
     */
    public List<Project> getDeletedProjectsByUser(Long userId) {
        return projectRepository.findByUserIdAndDeletedTrue(userId);
    }

    /**
     * Busca un proyecto activo por su identificador, validando que pertenece al usuario.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario
     * @return proyecto encontrado
     * @throws RuntimeException si no existe, está en la papelera o no pertenece al usuario
     */
    public Project getProjectByIdAndUser(Long projectId, Long userId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getUser().getId().equals(userId) && !p.isDeleted())
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));
    }

    /**
     * Crea un nuevo proyecto para un usuario y registra el evento en el historial.
     *
     * @param name        nombre del proyecto
     * @param description descripción opcional
     * @param category    categoría del proyecto
     * @param status      estado inicial; si es {@code null} se usa {@code TODO}
     * @param priority    prioridad inicial; si es {@code null} se usa {@code MEDIUM}
     * @param userId      identificador del usuario propietario
     * @return proyecto creado y persistido
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
     * Actualiza los datos de un proyecto y registra el evento en el historial.
     * Si el estado cambia, registra un evento {@code PROJECT_STATUS_CHANGED} en lugar de {@code PROJECT_EDITED}.
     *
     * @param projectId   identificador del proyecto
     * @param name        nuevo nombre
     * @param description nueva descripción
     * @param category    nueva categoría
     * @param status      nuevo estado
     * @param priority    nueva prioridad
     * @param userId      identificador del usuario propietario
     * @return proyecto actualizado
     */
    public Project updateProject(Long projectId, String name, String description,
                                 TaskCategory category, TaskStatus status,
                                 TaskPriority priority, Long userId) {
        Project project = getProjectByIdAndUser(projectId, userId);
        TaskStatus oldStatus = project.getStatus();

        // Validar que todas las tareas estén completadas antes de marcar el proyecto como DONE
        if (status == TaskStatus.DONE && oldStatus != TaskStatus.DONE) {
            boolean hasPendingTasks = taskRepository
                    .existsByProjectIdAndStatusNotInAndDeletedFalse(
                            projectId,
                            List.of(TaskStatus.DONE, TaskStatus.CANCELLED)
                    );
            if (hasPendingTasks) {
                throw new RuntimeException("No puedes completar este proyecto porque tiene tareas pendientes");
            }
        }

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
     * Envía un proyecto a la papelera (soft delete).
     * El proyecto no se elimina físicamente; se marca como eliminado con la fecha actual.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario propietario
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
     * Restaura un proyecto desde la papelera.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario propietario
     * @return proyecto restaurado
     * @throws RuntimeException si el proyecto no se encuentra en la papelera
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
     * Elimina definitivamente un proyecto de la base de datos.
     * Se usa desde el vaciado manual o automático de la papelera.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario, usado para registrar el evento
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
     * Elimina físicamente los proyectos en papelera cuya antigüedad supera el periodo de retención.
     *
     * @param retentionDays días de retención configurados en {@link com.taskmaster.taskmasterbackend.model.UserSettings}
     */
    public void purgeExpiredProjects(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Project> expired = projectRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);
        projectRepository.deleteAll(expired);
    }
}
