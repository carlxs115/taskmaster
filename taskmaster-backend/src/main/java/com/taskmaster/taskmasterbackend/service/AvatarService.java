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

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarService {

    private final UserRepository userRepository;
    private final AvatarStorageService storageService;

    /** Tipos MIME aceptados. Se validan en el servicio para blindar también ataques desde otros controladores. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/png", "image/jpeg");

    /** Tamaño máximo en bytes (2 MB). Coincide con spring.servlet.multipart.max-file-size. */
    private static final long MAX_SIZE_BYTES = 2 * 1024 * 1024;

    /**
     * Sube o reemplaza el avatar de un usuario.
     *
     * @param userId id del usuario
     * @param file   fichero multipart recibido del cliente (ya recortado a círculo y redimensionado)
     * @return el User actualizado, con el nuevo avatarPath
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
     * Obtiene los bytes del avatar de un usuario.
     * Devuelve null si el usuario no tiene avatar.
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
     * Borra el avatar de un usuario (fichero + campo en BD).
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

    // ---------- Helpers privados ----------

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

    private String resolveExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> throw new IllegalArgumentException("Content-Type no soportado: " + contentType);
        };
    }

    /** Par de bytes + Content-Type para la respuesta HTTP. Record simple, sin servicio detrás. */
    public record AvatarData(byte[] bytes, String contentType) {}
}
