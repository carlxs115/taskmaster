package com.taskmaster.service;

import com.taskmaster.model.Project;
import com.taskmaster.model.User;
import com.taskmaster.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SERVICIO DE PROJECT
 *
 * Gestiona toda la lógica de negocio relacionada con proyectos.
 * Cada operación valida que el usuario autenticado sea el propietario
 * del proyecto — así evitamos que un usuario acceda a datos de otro.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;

    /**
     * Devuelve todos los proyectos de un usuario.
     */
    public List<Project> getProjectByUser(Long userId) {
        return projectRepository.findByUserId(userId);
    }

    /**
     * Busca un proyecto por id validando que pertenece al usuario.
     *
     * @throws RuntimeException si no existe o no pertenece al usuario
     */
    public Project getProjectByIdAndUser(Long projectId, Long userId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getUser().getId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado o acceso denegado"));
    }

    /**
     * Crea un nuevo proyecto para un usuario.
     */
    public Project createProject(String name, String description, Long userId) {
        User user = userService.findById(userId);

        Project project = Project.builder()
                .name(name)
                .description(description)
                .user(user)
                .build();

        return projectRepository.save(project);
    }

    /**
     * Actualiza el nombre y descripción de un proyecto.
     * Valida que el proyecto pertenece al usuario antes de modificarlo.
     */
    public Project updateProject(Long projectId, String name, String description, Long userId) {
        Project project = getProjectByIdAndUser(projectId, userId);

        project.setName(name);
        project.setDescription(description);

        return projectRepository.save(project);
    }

    /**
     * Elimina un proyecto.
     * Al tener cascade = ALL en la entidad, se borran también todas sus tareas.
     */
    public void deleteProject(Long projectId, Long userId) {
        Project project = getProjectByIdAndUser(projectId, userId);
        projectRepository.delete(project);
    }
}
