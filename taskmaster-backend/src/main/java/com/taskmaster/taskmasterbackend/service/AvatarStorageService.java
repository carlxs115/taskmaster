package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.config.AvatarStorageProperties;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Servicio de bajo nivel que encapsula toda la gestión del sistema de ficheros
 * para las fotos de perfil de los usuarios.
 *
 * <p>El resto de la aplicación no debe conocer rutas, extensiones ni operaciones
 * de fichero directamente: debe delegar en este servicio. Al arrancar la aplicación,
 * crea el directorio de avatares si no existe y elimina los ficheros huérfanos
 * (imágenes sin usuario asociado en la base de datos).</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarStorageService {

    private final AvatarStorageProperties properties;
    private final UserRepository userRepository;

    /** Ruta absoluta del directorio donde se almacenan los avatares. */
    private Path avatarsDirectory;

    /**
     * Inicializa el directorio de avatares y ejecuta la reconciliación con la base de datos.
     * Se ejecuta automáticamente una vez al arrancar la aplicación.
     *
     * @throws IllegalStateException si no se puede crear el directorio
     */
    @PostConstruct
    public void init() {
        this.avatarsDirectory = Paths.get(properties.getAvatarsDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(avatarsDirectory);
            log.info("Directorio de avatares listo en: {}", avatarsDirectory);
            cleanupOrphans();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo inicializar el directorio de avatares: " + avatarsDirectory, e);
        }
    }

    /**
     * Elimina los ficheros de avatar que no tienen ningún usuario asociado en la base de datos.
     * Evita acumular imágenes huérfanas, especialmente al usar H2 en memoria donde la base
     * de datos se vacía en cada reinicio.
     *
     * @throws IOException si ocurre un error al listar o eliminar ficheros
     */
    private void cleanupOrphans() throws IOException {
        Set<String> registeredAvatars = new HashSet<>(userRepository.findAllAvatarPaths());

        try (Stream<Path> files = Files.list(avatarsDirectory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String filename = file.getFileName().toString();
                if (!registeredAvatars.contains(filename)) {
                    try {
                        Files.delete(file);
                        log.info("Avatar huérfano eliminado: {}", filename);
                    } catch (IOException e) {
                        log.warn("No se pudo eliminar el avatar huérfano {}: {}", filename, e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Guarda los bytes de una imagen en el directorio de avatares con un nombre único generado por UUID.
     *
     * @param imageBytes contenido binario de la imagen
     * @param extension  extensión del fichero sin punto ({@code "png"} o {@code "jpg"})
     * @return nombre del fichero guardado (ruta relativa al directorio de avatares)
     * @throws IOException si ocurre un error al escribir el fichero
     */
    public String save(byte[] imageBytes, String extension) throws IOException {
        String filename = UUID.randomUUID() + "." + extension.toLowerCase();
        Path target = avatarsDirectory.resolve(filename);
        Files.write(target, imageBytes);
        log.debug("Avatar guardado: {}", filename);
        return filename;
    }

    /**
     * Lee los bytes de un avatar existente.
     * Incluye protección contra ataques de path traversal.
     *
     * @param filename nombre del fichero devuelto por {@link #save}
     * @return contenido binario de la imagen
     * @throws IOException       si ocurre un error al leer el fichero
     * @throws SecurityException si la ruta resuelta apunta fuera del directorio de avatares
     */
    public byte[] load(String filename) throws IOException {
        Path file = avatarsDirectory.resolve(filename).normalize();

        // Defensa contra path traversal: el fichero resuelto debe seguir dentro del directorio de avatares
        if (!file.startsWith(avatarsDirectory)) {
            throw new SecurityException("Ruta de avatar inválida: " + filename);
        }

        return Files.readAllBytes(file);
    }

    /**
     * Elimina un avatar del sistema de ficheros.
     * No lanza excepción si el fichero ya no existe.
     * Incluye protección contra path traversal.
     *
     * @param filename nombre del fichero a eliminar
     */
    public void delete(String filename) {
        if (filename == null || filename.isBlank()) return;
        try {
            Path file = avatarsDirectory.resolve(filename).normalize();
            if (!file.startsWith(avatarsDirectory)) {
                log.warn("Intento de borrado fuera del directorio de avatares: {}", filename);
                return;
            }
            Files.deleteIfExists(file);
            log.debug("Avatar borrado: {}", filename);
        } catch (IOException e) {
            // No propagamos: si falla el borrado del fichero, no queremos tumbar la operación principal
            log.warn("No se pudo borrar el avatar {}: {}", filename, e.getMessage());
        }
    }

    /**
     * Resuelve el tipo MIME de un fichero de avatar a partir de su extensión.
     *
     * @param filename nombre del fichero
     * @return tipo MIME ({@code "image/png"}, {@code "image/jpeg"} o {@code "application/octet-stream"})
     */
    public String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
