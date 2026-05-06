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
 * de fichero directamente: debe delegar en este servicio.</p>
 *
 * <p>Al arrancar la aplicación ({@link #init()}), crea el directorio de avatares
 * si no existe y elimina los ficheros huérfanos (imágenes en disco sin usuario
 * asociado en la base de datos).</p>
 *
 * @author Carlos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarStorageService {

    private final AvatarStorageProperties properties;
    private final UserRepository userRepository;

    /** Ruta absoluta y normalizada del directorio donde se almacenan los avatares. */
    private Path avatarsDirectory;

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    /**
     * Inicializa el directorio de avatares y ejecuta la reconciliación con la BD.
     * Spring llama a este método automáticamente una vez al arrancar la aplicación,
     * gracias a {@code @PostConstruct}.
     *
     * @throws IllegalStateException si no se puede crear el directorio de avatares
     */
    @PostConstruct
    public void init() {
        // Normalizamos la ruta para eliminar segmentos relativos como ".." o "."
        this.avatarsDirectory = Paths.get(properties.getAvatarsDir()).toAbsolutePath().normalize();
        try {
            // Crea el directorio y todos los directorios padre si no existen
            Files.createDirectories(avatarsDirectory);
            log.info("Directorio de avatares listo en: {}", avatarsDirectory);

            // Limpiamos ficheros sin usuario asociado para no acumular basura en disco
            cleanupOrphans();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo inicializar el directorio de avatares: " + avatarsDirectory, e);
        }
    }

    // -------------------------------------------------------------------------
    // Operaciones de fichero
    // -------------------------------------------------------------------------

    /**
     * Guarda los bytes de una imagen en el directorio de avatares
     * con un nombre único generado por UUID para evitar colisiones.
     *
     * @param imageBytes contenido binario de la imagen
     * @param extension  extensión del fichero sin punto ({@code "png"} o {@code "jpg"})
     * @return nombre del fichero guardado (solo el nombre, no la ruta completa)
     * @throws IOException si ocurre un error al escribir el fichero en disco
     */
    public String save(byte[] imageBytes, String extension) throws IOException {
        // UUID garantiza nombres únicos sin necesidad de comprobar duplicados
        String filename = UUID.randomUUID() + "." + extension.toLowerCase();
        Path target = avatarsDirectory.resolve(filename);
        Files.write(target, imageBytes);
        log.debug("Avatar guardado: {}", filename);
        return filename;
    }

    /**
     * Lee los bytes de un avatar existente desde el disco.
     *
     * <p><b>Nota de seguridad:</b> un atacante podría enviar un nombre
     * de fichero como {@code "../../etc/passwd"} intentando leer ficheros fuera
     * del directorio de avatares. La normalización y comprobación de prefijo
     * evitan este ataque.</p>
     *
     * @param filename nombre del fichero devuelto por {@link #save}
     * @return contenido binario de la imagen
     * @throws IOException       si ocurre un error al leer el fichero
     * @throws SecurityException si la ruta resuelta apunta fuera del directorio de avatares
     */
    public byte[] load(String filename) throws IOException {
        // Resolvemos y normalizamos para que "../../../etc/passwd" quede como ruta absoluta real
        Path file = avatarsDirectory.resolve(filename).normalize();

        // SEGURIDAD: verificamos que la ruta final sigue dentro del directorio permitido.
        // Sin esta comprobación, un nombre como "../../otro-fichero" podría leer
        // ficheros del sistema fuera del directorio de avatares.
        if (!file.startsWith(avatarsDirectory)) {
            throw new SecurityException("Ruta de avatar inválida: acceso denegado");
        }

        return Files.readAllBytes(file);
    }

    /**
     * Elimina un avatar del sistema de ficheros.
     * No lanza excepción si el fichero ya no existe (operación idempotente).
     *
     * <p><b>Nota de seguridad:</b> incluye la misma protección contra path traversal
     * que {@link #load}.</p>
     *
     * @param filename nombre del fichero a eliminar
     */
    public void delete(String filename) {
        if (filename == null || filename.isBlank()) return;

        try {
            Path file = avatarsDirectory.resolve(filename).normalize();

            // SEGURIDAD: misma protección contra path traversal que en load()
            if (!file.startsWith(avatarsDirectory)) {
                log.warn("Intento de borrado fuera del directorio de avatares bloqueado");
                return;
            }

            Files.deleteIfExists(file);
            log.debug("Avatar borrado: {}", filename);

        } catch (IOException e) {
            // No propagamos el error: si falla el borrado del fichero físico,
            // no queremos que eso revierta la operación principal (p.ej. cambio de avatar)
            log.warn("No se pudo borrar el avatar {}: {}", filename, e.getMessage());
        }
    }

    /**
     * Resuelve el tipo MIME de un fichero de avatar a partir de su extensión.
     * Si la extensión no es reconocida, devuelve el tipo genérico binario.
     *
     * @param filename nombre del fichero
     * @return tipo MIME: {@code "image/png"}, {@code "image/jpeg"}
     *         o {@code "application/octet-stream"} si no se reconoce la extensión
     */
    public String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        // Tipo genérico para extensiones no reconocidas; el navegador intentará detectarlo
        return "application/octet-stream";
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Elimina los ficheros de avatar en disco que no tienen ningún usuario
     * asociado en la base de datos (ficheros huérfanos).
     *
     * <p>Esto puede ocurrir si la aplicación se cierra abruptamente durante una operación de guardado,
     * o si la BD se reinicia mientras quedan ficheros en disco de una sesión anterior.</p>
     *
     * @throws IOException si ocurre un error al listar los ficheros del directorio
     */
    private void cleanupOrphans() throws IOException {
        // Obtenemos todos los avatarPath registrados en BD para compararlos con el disco
        Set<String> registeredAvatars = new HashSet<>(userRepository.findAllAvatarPaths());

        try (Stream<Path> files = Files.list(avatarsDirectory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String filename = file.getFileName().toString();

                // Si el fichero en disco no está referenciado por ningún usuario, lo borramos
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


}
