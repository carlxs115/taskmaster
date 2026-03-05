package com.taskmaster.repository;

import com.taskmaster.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITORIO DE PROJECT
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Devuelve todos los proyectos de un usuario concreto.
     * Spring genera: SELECT * FROM projects WHERE user_id = ?
     */
    List<Project> findByUserId(Long userId);

    /**
     * Comprueba si un proyecto pertenece a un usuario concreto.
     * Útil para seguridad: evitar que un usuario acceda a proyectos ajenos.
     * Spring genera: SELECT COUNT(*) > 0 FROM projects WHERE id = ? AND user_id = ?
     */
    boolean existsByIdAndUserId(Long id, Long userId);
}
