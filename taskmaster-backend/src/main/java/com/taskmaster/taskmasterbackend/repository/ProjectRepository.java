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
 * y verificaciones de pertenencia para control de acceso.</p>
 *
 * @author Carlos
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Devuelve todos los proyectos activos (no eliminados) de un usuario.
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
     * Se usa para el vaciado automático según el periodo de retención configurado por el usuario.
     *
     * @param cutoffDate fecha límite; se devuelven los proyectos eliminados antes de esta fecha
     * @return lista de proyectos a eliminar definitivamente
     */
    List<Project> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoffDate);

    /**
     * Comprueba si un proyecto pertenece a un usuario concreto.
     * Se usa para evitar que un usuario acceda a proyectos ajenos.
     *
     * @param id     identificador del proyecto
     * @param userId identificador del usuario
     * @return {@code true} si el proyecto pertenece al usuario, {@code false} en caso contrario
     */
    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Cuenta los proyectos activos de un usuario.
     *
     * @param userId identificador del usuario
     * @return número de proyectos activos
     */
    long countByUserIdAndDeletedFalse(Long userId);
}
