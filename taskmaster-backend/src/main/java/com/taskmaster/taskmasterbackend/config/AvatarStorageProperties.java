package com.taskmaster.taskmasterbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * PROPIEDADES DE ALMACENAMIENTO DE AVATARES
 *
 * Lee las propiedades del application.properties que empiezan por "taskmaster.storage".
 * Usamos @ConfigurationProperties (en vez de @Value) porque es más tipado,
 * se documenta mejor y escala si añadimos más rutas en el futuro.
 *
 * Ejemplo en application.properties:
 *   taskmaster.storage.avatars-dir=${user.home}/.taskmaster/avatars
 */
@Configuration
@ConfigurationProperties(prefix = "taskmaster.storage")
@Data
public class AvatarStorageProperties {

    /**
     * Directorio donde se guardan las imágenes de perfil.
     * Por defecto: ~/.taskmaster/avatars
     */
    private String avatarsDir;
}
