package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserRepository userRepository;
    private final AvatarStorageService storageService;

    /** Tipos MIME aceptados para las fotos de perfil. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/png", "image/jpeg");

    /** Tamaño máximo permitido en bytes (2 MB). */
    private static final long MAX_SIZE_BYTES = 2 * 1024 * 1024;

    // -------------------------------------------------------------------------
    // Operaciones de avatar
    // -------------------------------------------------------------------------

    /**
     * Sube o reemplaza la foto de perfil de un usuario.
     *
     * <p>Pasos que realiza:</p>
     * <ol>
     *   <li>Valida el fichero (tamaño y tipo MIME)</li>
     *   <li>Guarda el nuevo fichero en disco con nombre UUID</li>
     *   <li>Actualiza el campo {@code avatarPath} del usuario en BD</li>
     *   <li>Elimina el fichero anterior del disco si existía</li>
     * </ol>
     *
     * <p>El borrado del anterior se hace <b>después</b> de guardar el nuevo
     * para evitar quedarse sin avatar si falla el guardado.</p>
     *
     * @param userId identificador del usuario
     * @param file   fichero multipart recibido del cliente
     * @return usuario actualizado con el nuevo {@code avatarPath}
     * @throws IOException       si ocurre un error al guardar el fichero en disco
     * @throws BusinessException si el fichero no supera la validación
     * @throws ResourceNotFoundException si el usuario no existe
     */
    @Transactional
    public User uploadAvatar(Long userId, MultipartFile file) throws IOException {
        validateFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

        // Guardamos la ruta anterior para borrarla después del guardado exitoso.
        // Si la borráramos antes y fallara el guardado, el usuario se quedaría sin avatar.
        String previousAvatar = user.getAvatarPath();

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BusinessException("Tipo de fichero no permitido. Solo se aceptan PNG y JPEG.");
        }
        String extension = resolveExtension(contentType);

        String newFilename = storageService.save(file.getBytes(), extension);

        // Actualizamos la BD con el nuevo nombre de fichero
        user.setAvatarPath(newFilename);
        userRepository.save(user);

        // Solo borramos el anterior una vez confirmado el guardado en BD
        if (previousAvatar != null) {
            storageService.delete(previousAvatar);
        }

        log.info("Avatar actualizado para usuario id {}: {}", userId, newFilename);
        return user;
    }

    /**
     * Obtiene los bytes del avatar de un usuario junto con su tipo MIME.
     *
     * @param userId identificador del usuario
     * @return {@link AvatarData} con los bytes de la imagen y su Content-Type,
     *         o {@code null} si el usuario no tiene avatar asignado
     * @throws IOException               si ocurre un error al leer el fichero del disco
     * @throws ResourceNotFoundException si el usuario no existe
     */
    public AvatarData getAvatar(Long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

        String filename = user.getAvatarPath();

        // Si el usuario no tiene avatar asignado devolvemos null, el controlador responderá con 204 No Content
        if (filename == null) return null;

        byte[] bytes = storageService.load(filename);
        String contentType = storageService.resolveContentType(filename);

        return new AvatarData(bytes, contentType);
    }

    /**
     * Elimina la foto de perfil de un usuario, tanto del disco como de la base de datos.
     * Si el usuario no tiene avatar asignado, no hace nada.
     *
     * @param userId identificador del usuario
     * @throws ResourceNotFoundException si el usuario no existe
     */
    @Transactional
    public void deleteAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        String filename = user.getAvatarPath();

        // Si no tiene avatar no hay nada que borrar
        if (filename == null) return;

        // Primero limpiamos la referencia en BD, luego borramos el fichero.
        // Si fallara el borrado del fichero, al menos la BD queda consistente.
        user.setAvatarPath(null);
        userRepository.save(user);

        storageService.delete(filename);
        log.info("Avatar eliminado para usuario id {}", userId);
    }

    // -------------------------------------------------------------------------
    // Métodos privados de validación
    // -------------------------------------------------------------------------

    /**
     * Valida que el fichero no esté vacío, no supere el tamaño máximo
     * y tenga un tipo MIME permitido (PNG o JPEG).
     *
     * @param file fichero a validar
     * @throws BusinessException si el fichero no supera alguna de las validaciones
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El fichero está vacío");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("El fichero excede el tamaño máximo de 2 MB");
        }
        String contentType = file.getContentType();

        // SEGURIDAD: validamos el MIME type declarado por el cliente.
        // Nota: el Content-Type puede ser manipulado por el cliente, pero es
        // una capa de validación adicional. AvatarStorageService guarda el fichero
        // con la extensión correcta independientemente del contenido real.
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("Tipo de fichero no permitido. Solo se aceptan PNG y JPEG.");
        }
    }

    /**
     * Resuelve la extensión del fichero a partir de su tipo MIME.
     *
     * @param contentType tipo MIME del fichero (ya validado en {@link #validateFile})
     * @return extensión sin punto ({@code "png"} o {@code "jpg"})
     * @throws BusinessException si el Content-Type no está soportado
     */
    private String resolveExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            // SEGURIDAD: mensaje genérico, no reflejamos el Content-Type recibido
            // porque lo envía el cliente y podría contener datos inesperados
            default -> throw new BusinessException("Tipo de fichero no soportado");
        };
    }

    // -------------------------------------------------------------------------
    // Tipos de datos
    // -------------------------------------------------------------------------

    /**
     * Par inmutable de bytes de imagen y Content-Type para la respuesta HTTP.
     * Usado por el controlador para construir la respuesta con el tipo correcto.
     *
     * @param bytes       contenido binario de la imagen
     * @param contentType tipo MIME de la imagen (p.ej. {@code "image/png"})
     */
    public record AvatarData(byte[] bytes, String contentType) {}
}
