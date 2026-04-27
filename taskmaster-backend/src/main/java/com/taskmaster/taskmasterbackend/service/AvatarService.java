package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Servicio que gestiona las fotos de perfil de los usuarios.
 *
 * <p>Coordina la validación del fichero subido, el almacenamiento físico
 * a través de {@link AvatarStorageService} y la actualización del campo
 * {@code avatarPath} en la entidad {@link User}.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarService {

    private final UserRepository userRepository;
    private final AvatarStorageService storageService;

    /** Tipos MIME aceptados para las fotos de perfil. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/png", "image/jpeg");

    /** Tamaño máximo permitido en bytes (2 MB). */
    private static final long MAX_SIZE_BYTES = 2 * 1024 * 1024;

    /**
     * Sube o reemplaza la foto de perfil de un usuario.
     * Si el usuario ya tenía avatar, el fichero anterior se elimina del disco.
     *
     * @param userId identificador del usuario
     * @param file   fichero multipart recibido del cliente
     * @return usuario actualizado con el nuevo {@code avatarPath}
     * @throws IOException              si ocurre un error al guardar el fichero
     * @throws IllegalArgumentException si el fichero no supera la validación
     */
    @Transactional
    public User uploadAvatar(Long userId, MultipartFile file) throws IOException {
        validateFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        // Si ya tenía avatar, lo borramos del disco antes de guardar el nuevo (evita huérfanos).
        String previousAvatar = user.getAvatarPath();

        String extension = resolveExtension(file.getContentType());
        String newFilename = storageService.save(file.getBytes(), extension);

        user.setAvatarPath(newFilename);
        userRepository.save(user);

        if (previousAvatar != null) {
            storageService.delete(previousAvatar);
        }

        log.info("Avatar actualizado para usuario {}: {}", userId, newFilename);
        return user;
    }

    /**
     * Obtiene los bytes del avatar de un usuario junto con su tipo MIME.
     *
     * @param userId identificador del usuario
     * @return {@link AvatarData} con los bytes e imagen y su Content-Type,
     *         o {@code null} si el usuario no tiene avatar
     * @throws IOException si ocurre un error al leer el fichero
     */
    public AvatarData getAvatar(Long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        String filename = user.getAvatarPath();
        if (filename == null) return null;

        byte[] bytes = storageService.load(filename);
        String contentType = storageService.resolveContentType(filename);
        return new AvatarData(bytes, contentType);
    }

    /**
     * Elimina la foto de perfil de un usuario, tanto del disco como de la base de datos.
     *
     * @param userId identificador del usuario
     */
    @Transactional
    public void deleteAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        String filename = user.getAvatarPath();
        if (filename == null) return;

        user.setAvatarPath(null);
        userRepository.save(user);
        storageService.delete(filename);

        log.info("Avatar eliminado para usuario {}", userId);
    }

    /**
     * Valida que el fichero no esté vacío, no supere el tamaño máximo
     * y tenga un tipo MIME permitido (PNG o JPEG).
     *
     * @param file fichero a validar
     * @throws IllegalArgumentException si el fichero no supera alguna validación
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El fichero está vacío");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("El fichero excede el tamaño máximo de 2 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Tipo de fichero no permitido. Solo PNG o JPEG.");
        }
    }

    /**
     * Resuelve la extensión del fichero a partir de su tipo MIME.
     *
     * @param contentType tipo MIME del fichero
     * @return extensión sin punto ({@code "png"} o {@code "jpg"})
     * @throws IllegalArgumentException si el Content-Type no está soportado
     */
    private String resolveExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> throw new IllegalArgumentException("Content-Type no soportado: " + contentType);
        };
    }

    /**
     * Par inmutable de bytes de imagen y Content-Type para la respuesta HTTP.
     *
     * @param bytes       contenido binario de la imagen
     * @param contentType tipo MIME de la imagen
     */
    public record AvatarData(byte[] bytes, String contentType) {}
}
