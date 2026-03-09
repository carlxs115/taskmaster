package com.taskmaster.repository;

import com.taskmaster.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REPOSITORIO DE PROJECT
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Devuelve todos los proyectos activos de un usuario concreto (no eliminados).
     * Spring genera: SELECT * FROM projects WHERE user_id = ? AND deleted = false
     */
    List<Project> findByUserIdAndDeletedFalse(Long userId);

    /**
     * Proyectos en la papelera de un usuario.
     * Spring genera: SELECT * FROM projects WHERE user_id = ? AND deleted = true
     */
    List<Project> findByUserIdAndDeletedTrue(Long userId);

    /**
     * Proyectos en papelera cuya fecha de eliminación es anterior a una fecha dada.
     * Se usa para el vaciado automático según el periodo configurado por el usuario.
     * Spring genera: SELECT * FROM projects WHERE deleted = true AND deleted_at < ?
     */
    List<Project> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoffDate);

    /**
     * Comprueba si un proyecto pertenece a un usuario concreto.
     * Útil para seguridad: evitar que un usuario acceda a proyectos ajenos.
     * Spring genera: SELECT COUNT(*) > 0 FROM projects WHERE id = ? AND user_id = ?
     */
    boolean existsByIdAndUserId(Long id, Long userId);
}
