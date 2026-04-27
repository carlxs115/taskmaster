package com.taskmaster.taskmasterbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades de configuración para el almacenamiento de avatares.
 *
 * <p>Lee las propiedades del {@code application.properties} con el prefijo
 * {@code taskmaster.storage}. Ejemplo:</p>
 * <pre>
 * taskmaster.storage.avatars-dir=${user.home}/.taskmaster/avatars
 * </pre>
 *
 * @author Carlos
 */
@Configuration
@ConfigurationProperties(prefix = "taskmaster.storage")
@Data
public class AvatarStorageProperties {

    /**
     * Directorio donde se almacenan las imágenes de perfil de los usuarios.
     * Por defecto: {@code ~/.taskmaster/avatars}.
     */
    private String avatarsDir;
}
