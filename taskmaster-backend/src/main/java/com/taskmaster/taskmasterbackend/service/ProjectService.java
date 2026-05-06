package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final ActivityLogService activityLogService;
    private final TaskRepository taskRepository;

    // -------------------------------------------------------------------------
    // Lectura
    // -------------------------------------------------------------------------

    /**
     * Devuelve todos los proyectos activos (no eliminados) de un usuario.
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
     * @return lista de proyectos marcados como eliminados
     */
    public List<Project> getDeletedProjectsByUser(Long userId) {
        return projectRepository.findByUserIdAndDeletedTrue(userId);
    }

    /**
     * Busca un proyecto activo por su identificador, validando que pertenece al usuario.
     * Se usa también como guard en TaskService para validar acceso antes de operar.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario
     * @return proyecto encontrado
     * @throws ResourceNotFoundException si no existe, está eliminado o no pertenece al usuario
     */
    public Project getProjectByIdAndUser(Long projectId, Long userId) {
        return projectRepository.findById(projectId)
                // Verificamos que el proyecto pertenece al usuario y no está en la papelera
                .filter(p -> p.getUser().getId().equals(userId) && !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado"));
    }

    // -------------------------------------------------------------------------
    // Escritura
    // -------------------------------------------------------------------------

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
    @Transactional
    public Project createProject(String name, String description, TaskCategory category,
                                 TaskStatus status, TaskPriority priority, Long userId) {

        User user = userService.findById(userId);

        Project project = Project.builder()
                .name(name)
                .description(description)
                .category(category)
                // Valores por defecto si no se especifican
                .status(status != null ? status : TaskStatus.TODO)
                .priority(priority != null ? priority : TaskPriority.MEDIUM)
                .user(user)
                .deleted(false)
                .build();

        Project saved = projectRepository.save(project);

        activityLogService.log(userId, ActionType.PROJECT_CREATED, "PROJECT", saved.getId(), saved.getName());

        return saved;
    }

    /**
     * Actualiza los datos de un proyecto y registra el evento en el historial.
     *
     * <p>Si el estado cambia, se registra {@code PROJECT_STATUS_CHANGED} con los
     * valores anterior y nuevo. En cualquier otro caso se registra {@code PROJECT_EDITED}.</p>
     *
     * <p><b>Regla de negocio:</b> no se puede marcar un proyecto como {@code DONE} si tiene tareas pendientes.</p>
     *
     * @param projectId   identificador del proyecto
     * @param name        nuevo nombre
     * @param description nueva descripción
     * @param category    nueva categoría
     * @param status      nuevo estado
     * @param priority    nueva prioridad
     * @param userId      identificador del usuario propietario
     * @return proyecto actualizado
     * @throws BusinessException si se intenta completar un proyecto con tareas pendientes
     */
    @Transactional
    public Project updateProject(Long projectId, String name, String description, TaskCategory category,
                                 TaskStatus status, TaskPriority priority, Long userId) {

        Project project = getProjectByIdAndUser(projectId, userId);
        TaskStatus oldStatus = project.getStatus();

        // Regla de negocio: no se puede completar un proyecto con tareas pendientes
        if (status == TaskStatus.DONE && oldStatus != TaskStatus.DONE) {
            boolean hasPendingTasks = taskRepository
                    .existsByProjectIdAndStatusNotInAndDeletedFalse(
                            projectId, List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
            if (hasPendingTasks) {
                throw new BusinessException("No puedes completar este proyecto porque tiene tareas pendientes");
            }
        }

        project.setName(name);
        project.setDescription(description);
        project.setCategory(category);
        project.setStatus(status);
        project.setPriority(priority);

        Project saved = projectRepository.save(project);

        // Registramos el tipo de cambio más relevante en el historial
        if (oldStatus != null && status != null && !oldStatus.equals(status)) {
            activityLogService.log(userId, ActionType.PROJECT_STATUS_CHANGED, "PROJECT", saved.getId(),
                    saved.getName(), oldStatus.name(), status.name());
        } else {
            activityLogService.log(userId, ActionType.PROJECT_EDITED, "PROJECT",
                    saved.getId(), saved.getName());
        }

        return saved;
    }

    /**
     * Envía un proyecto a la papelera (soft delete).
     * El proyecto no se elimina físicamente: se marca como eliminado con la fecha actual.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario propietario
     */
    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        Project project = getProjectByIdAndUser(projectId, userId);

        project.setDeleted(true);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);

        activityLogService.log(userId, ActionType.PROJECT_DELETED, "PROJECT", projectId, project.getName());
    }

    /**
     * Restaura un proyecto desde la papelera, limpiando su fecha de eliminación.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario propietario
     * @return proyecto restaurado
     * @throws ResourceNotFoundException si el proyecto no está en la papelera del usuario
     */
    @Transactional
    public Project restoreProject(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                // Verificamos que pertenece al usuario y está en la papelera
                .filter(p -> p.getUser().getId().equals(userId) && p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado en la papelera"));

        project.setDeleted(false);
        project.setDeletedAt(null); // limpiamos la fecha de eliminación
        Project saved = projectRepository.save(project);

        activityLogService.log(project.getUser().getId(), ActionType.PROJECT_RESTORED,
                "PROJECT", saved.getId(), saved.getName());

        return saved;
    }

    /**
     * Elimina definitivamente un proyecto de la base de datos.
     * Usado desde el vaciado manual o automático de la papelera.
     *
     * @param projectId identificador del proyecto
     * @param userId    identificador del usuario (para registrar el evento)
     * @throws ResourceNotFoundException si el proyecto no existe
     */
    @Transactional
    public void deletePermanently(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));

        String name = project.getName(); // capturamos el nombre antes de borrar
        projectRepository.deleteById(projectId);

        activityLogService.log(userId, ActionType.PROJECT_PERMANENTLY_DELETED, "PROJECT", projectId, name);
    }

    /**
     * Elimina físicamente los proyectos en papelera cuya antigüedad supera
     * el periodo de retención. Llamado por {@link com.taskmaster.taskmasterbackend.TrashScheduler}.
     *
     * @param retentionDays días de retención configurados en {@link com.taskmaster.taskmasterbackend.model.UserSettings}
     */
    @Transactional
    public void purgeExpiredProjects(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Project> expired = projectRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);

        if (!expired.isEmpty()) {
            log.info("Purgando {} proyectos expirados (retención: {} días)",
                    expired.size(), retentionDays);
            projectRepository.deleteAll(expired);
        }
    }

    /**
     * Elimina definitivamente todos los proyectos en la papelera del usuario.
     *
     * @param userId identificador del usuario
     */
    @Transactional
    public void emptyTrash(Long userId) {
        List<Project> deleted = getDeletedProjectsByUser(userId);

        // Eliminamos todos de golpe y registramos cada uno en el historial
        projectRepository.deleteAll(deleted);

        for (Project project : deleted) {
            activityLogService.log(userId, ActionType.PROJECT_PERMANENTLY_DELETED,
                    "PROJECT", project.getId(), project.getName());
        }
    }
}