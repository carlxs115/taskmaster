package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos para la entidad {@link UserSettings}.
 *
 * <p>Permite acceder a la configuración de un usuario navegando
 * la relación {@code UserSettings → User} mediante su identificador.</p>
 *
 * @author Carlos
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /**
     * Busca la configuración de un usuario por su identificador.
     *
     * @param userId identificador del usuario
     * @return {@link Optional} con la configuración si existe, vacío en caso contrario
     */
    Optional<UserSettings> findByUserId(Long userId);
}
