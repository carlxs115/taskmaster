package com.taskmaster.repository;

import com.taskmaster.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * REPOSITORIO DE USER
 *
 * JpaRepository<User, Long> nos da gratis estos métodos sin escribir nada:
 *   - save(user)          → INSERT o UPDATE
 *   - findById(id)        → SELECT WHERE id = ?
 *   - findAll()           → SELECT * FROM users
 *   - delete(user)        → DELETE
 *   - existsById(id)      → comprueba si existe
 *
 * Nosotros solo añadimos los métodos extra que necesitamos.
 * Spring Data JPA lee el nombre del método y genera el SQL automáticamente.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Spring genera: SELECT * FROM users WHERE username = ?
     * Optional → puede devolver un User o estar vacío (si no existe)
     */
    Optional<User> findByUsername(String username);

    /**
     * Spring genera: SELECT * FROM users WHERE email = ?
     * Optional → puede devolver un Email o estar vacío (si no existe)
     */
    Optional<User> findByEmail(String email);

    /**
     * Spring genera: SELECT COUNT(*) > 0 FROM users WHERE username = ?
     * Útil para comprobar si un username ya está registrado
     */
    boolean existsByUsername(String username);

    /**
     * Spring genera: SELECT COUNT(*) > 0 FROM users WHERE email = ?
     * Útil para comprobar si un email ya está registrado
     */
    boolean existsByEmail(String email);
}
