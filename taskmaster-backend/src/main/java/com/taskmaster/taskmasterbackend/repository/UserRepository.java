package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos para la entidad {@link User}.
 *
 * <p>Además de las operaciones CRUD heredadas de {@link JpaRepository},
 * proporciona consultas para buscar usuarios por credenciales, verificar
 * disponibilidad de username y email, y recuperar rutas de avatares.</p>
 *
 * @author Carlos
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su nombre de usuario.
     *
     * @param username nombre de usuario a buscar
     * @return {@link Optional} con el usuario si existe, vacío en caso contrario
     */
    Optional<User> findByUsername(String username);

    /**
     * Comprueba si ya existe un usuario con el nombre de usuario indicado.
     * Se usa durante el registro para evitar duplicados.
     *
     * @param username nombre de usuario a comprobar
     * @return {@code true} si ya está registrado, {@code false} en caso contrario
     */
    boolean existsByUsername(String username);

    /**
     * Comprueba si ya existe un usuario con el correo electrónico indicado.
     * Se usa durante el registro para evitar duplicados.
     *
     * @param email correo electrónico a comprobar
     * @return {@code true} si ya está registrado, {@code false} en caso contrario
     */
    boolean existsByEmail(String email);

    /**
     * Devuelve todas las rutas de avatar registradas en la base de datos.
     * Se usa en {@code AvatarStorageService} al arrancar la aplicación
     * para detectar y limpiar ficheros de imagen huérfanos.
     *
     * @return lista de rutas relativas de avatar no nulas
     */
    @Query("SELECT u.avatarPath FROM User u WHERE u.avatarPath IS NOT NULL")
    List<String> findAllAvatarPaths();
}
