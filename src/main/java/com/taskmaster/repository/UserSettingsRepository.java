package com.taskmaster.repository;

import com.taskmaster.model.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * REPOSITORIO DE USERSETTINGS
 *
 * Permite acceder a la configuración del usuario por su userId,
 * navegando la relación UserSettings -> User.
 *
 * Spring genera: SELECT * FROM user_settings WHERE user_id = ?
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUserId(Long userId);
}
