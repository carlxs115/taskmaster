package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link Project}.
 *
 * <p>Incluye consultas para gestionar proyectos activos, proyectos en la papelera
 * y contadores para las estadísticas del usuario.</p>
 *
 * @author Carlos
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Devuelve todos los proyectos activos (no eliminados) de un usuario.
     * Usa el índice {@code idx_projects_user_id} definido en la entidad.
     *
     * @param userId identificador del usuario
     * @return lista de proyectos activos del usuario
     */
    List<Project> findByUserIdAndDeletedFalse(Long userId);

    /**
     * Devuelve los proyectos en la papelera de un usuario.
     *
     * @param userId identificador del usuario
     * @return lista de proyectos eliminados del usuario
     */
    List<Project> findByUserIdAndDeletedTrue(Long userId);

    /**
     * Devuelve los proyectos en papelera cuya fecha de eliminación es anterior a la indicada.
     * Usado por {@link com.taskmaster.taskmasterbackend.TrashScheduler} para el vaciado
     * automático según el periodo de retención configurado por el usuario.
     *
     * @param cutoffDate fecha límite; se devuelven los proyectos eliminados antes de esta fecha
     * @return lista de proyectos a eliminar definitivamente
     */
    List<Project> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoffDate);

    /**
     * Cuenta los proyectos activos de un usuario.
     * Usado en {@link com.taskmaster.taskmasterbackend.service.UserService#getStats}
     * para calcular las estadísticas de la pantalla de inicio.
     *
     * @param userId identificador del usuario
     * @return número de proyectos activos
     */
    long countByUserIdAndDeletedFalse(Long userId);
}