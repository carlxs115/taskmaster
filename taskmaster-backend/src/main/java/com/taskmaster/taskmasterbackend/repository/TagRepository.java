package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * REPOSITORIO DE TAG
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Busca una etiqueta por nombre.
     * Spring genera: SELECT * FROM tags WHERE name = ?
     */
    Optional<Tag> findByName(String name);

    /**
     * Comprueba si ya existe una etiqueta con ese nombre.
     * Spring genera: SELECT COUNT(*) > 0 FROM tags WHERE name = ?
     */
    boolean existsByName(String name);
}
