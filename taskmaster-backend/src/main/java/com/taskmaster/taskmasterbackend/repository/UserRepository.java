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
     * Usado en {@code UserDetailsServiceImpl.loadUserByUsername} y
     * {@code SecurityUtils.getUserId} para resolver el usuario autenticado.
     *
     * @param username nombre de usuario a buscar
     * @return {@link Optional} con el usuario si existe, vacío en caso contrario
     */
    Optional<User> findByUsername(String username);

    /**
     * Comprueba si ya existe un usuario con el nombre de usuario indicado.
     * Usado en {@code UserService.register} y {@code UserService.updateProfile}
     * para evitar duplicados antes de persistir.
     *
     * @param username nombre de usuario a comprobar
     * @return {@code true} si ya está registrado, {@code false} en caso contrario
     */
    boolean existsByUsername(String username);

    /**
     * Busca un usuario por su correo electrónico.
     * Usado en {@code UserDetailsServiceImpl.loadUserByUsername} para permitir
     * el login con email además de con username.
     *
     * @param email correo electrónico a buscar
     * @return {@link Optional} con el usuario si existe, vacío en caso contrario
     */
    Optional<User> findByEmail(String email);

    /**
     * Comprueba si ya existe un usuario con el correo electrónico indicado.
     * Usado en {@code UserService.register} y {@code UserService.updateProfile}
     * para evitar duplicados antes de persistir.
     *
     * @param email correo electrónico a comprobar
     * @return {@code true} si ya está registrado, {@code false} en caso contrario
     */
    boolean existsByEmail(String email);

    /**
     * Devuelve todas las rutas de avatar registradas en la base de datos.
     * Usado en {@code AvatarStorageService.cleanupOrphans} al arrancar la aplicación
     * para comparar con los ficheros en disco y eliminar los huérfanos.
     *
     * <p>El filtro {@code WHERE u.avatarPath IS NOT NULL} evita incluir usuarios
     * sin avatar en la lista de comparación.</p>
     *
     * @return lista de rutas relativas de avatar de todos los usuarios que tienen una
     */
    @Query("SELECT u.avatarPath FROM User u WHERE u.avatarPath IS NOT NULL")
    List<String> findAllAvatarPaths();
}
