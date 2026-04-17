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
 * SERVICIO DE ALMACENAMIENTO DE AVATARES
 *
 * Encapsula toda la gestión del sistema de ficheros para las fotos de perfil.
 * El resto de la aplicación (controladores, otros servicios) no debería saber
 * NADA de rutas, extensiones o ficheros: solo llaman a estos métodos.
 *
 * Responsabilidades:
 *   - Crear el directorio de avatares si no existe (al arrancar la app).
 *   - Reconciliar filesystem con BD al arrancar (borra huérfanos).
 *   - Guardar una nueva imagen generando un nombre único (UUID).
 *   - Leer una imagen existente.
 *   - Borrar una imagen cuando el usuario cambia o elimina su foto.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarStorageService {

    private final AvatarStorageProperties properties;
    private final UserRepository userRepository;
    private Path avatarsDirectory;

    /**
     * @PostConstruct -> Se ejecuta UNA vez al arrancar la app, después de inyectar dependencias.
     * Resolvemos la ruta, creamos el directorio si no existe, y limpiamos ficheros huérfanos.
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
     * Reconciliación entre BD y filesystem.
     *
     * Recorre todos los ficheros de la carpeta de avatares y borra los que no tengan
     * un usuario asociado en la BD. Evita acumular basura cuando:
     *   - Se usa H2 en memoria (BD vacía tras reinicio → todos los ficheros son huérfanos).
     *   - Un usuario se borró directamente por SQL sin pasar por el servicio.
     *   - Cualquier incoherencia entre BD y filesystem.
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
     * Guarda los bytes de una imagen en el directorio de avatares con un nombre único.
     *
     * @param imageBytes contenido binario de la imagen (ya procesada y redimensionada por el cliente)
     * @param extension  extensión sin punto: "png" o "jpg"
     * @return nombre del fichero guardado (path relativo al directorio de avatares)
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
     *
     * @param filename nombre devuelto por save() (p. ej. "uuid.png")
     * @return contenido binario de la imagen
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
     * Borra un avatar del filesystem. No falla si el fichero ya no existe.
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
     * Resuelve el tipo MIME a partir del nombre del fichero, para el Content-Type del endpoint GET.
     */
    public String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
