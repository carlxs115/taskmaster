package com.taskmaster.taskmasterbackend.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Rutas efectivas de los datos de TaskMaster que intervienen en la copia de seguridad
 * y en la restauración: el fichero de la base de datos SQLite y el directorio de avatares.
 *
 * <p>Se deducen siempre de la configuración efectiva ({@code spring.datasource.url} y
 * {@code taskmaster.storage.avatars-dir}), nunca de {@code user.home}: en el entorno de
 * pruebas ambas se redirigen con variables de entorno a otro directorio y la copia y la
 * restauración tienen que ocurrir allí.</p>
 *
 * <p>Los directorios de trabajo cuelgan del directorio que contiene la base de datos:</p>
 * <ul>
 *   <li>{@code restore-pending/}: copia validada que se aplicará en el siguiente arranque.</li>
 *   <li>{@code restore-previous/<marca>/}: estado anterior a cada restauración aplicada.</li>
 *   <li>{@code restore-failed-<marca>/}: copia pendiente cuya aplicación falló.</li>
 *   <li>{@code restore-failed.aviso}: aviso de que la última restauración no se aplicó.</li>
 *   <li>{@code backup-tmp-<uuid>}, {@code restore-upload-<uuid>.zip},
 *       {@code restore-staging-<uuid>}: temporales.</li>
 * </ul>
 *
 * <p>El directorio de avatares tiene que colgar del directorio de datos: la restauración
 * lo mueve junto a la base de datos y lo guarda en {@code restore-previous/}. Si no es así,
 * la copia de seguridad no está disponible (ver {@link #avatarsInDataDir()}).</p>
 *
 * @param database   ruta absoluta y normalizada del fichero de la base de datos
 * @param avatarsDir ruta absoluta y normalizada del directorio de avatares
 *
 * @author Carlos
 */
public record BackupPaths(Path database, Path avatarsDir) {

    /** Propiedad con la URL JDBC de la base de datos. */
    static final String DATASOURCE_URL_PROPERTY = "spring.datasource.url";

    /** Propiedad con el directorio de avatares (la misma que lee {@link AvatarStorageProperties}). */
    static final String AVATARS_DIR_PROPERTY = "taskmaster.storage.avatars-dir";

    /** Prefijo de las URL JDBC de SQLite. */
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    /** Nombre del directorio con la restauración pendiente. */
    public static final String PENDING_DIR = "restore-pending";

    /** Nombre del directorio que guarda el estado anterior a cada restauración. */
    public static final String PREVIOUS_DIR = "restore-previous";

    /** Fichero que marca una restauración pendiente como completa y lista para aplicarse. */
    public static final String READY_MARKER = "LISTA";

    /** Nombre de la base de datos dentro de la copia y de {@code restore-pending/}. */
    public static final String BACKUP_DATABASE_ENTRY = "taskmaster.db";

    /** Nombre del directorio de avatares dentro de la copia y de {@code restore-pending/}. */
    public static final String BACKUP_AVATARS_DIR = "avatars";

    /** Prefijo del directorio y del zip temporales al crear una copia. */
    public static final String BACKUP_TMP_PREFIX = "backup-tmp-";

    /** Prefijo del zip subido al preparar una restauración. */
    public static final String RESTORE_UPLOAD_PREFIX = "restore-upload-";

    /** Prefijo del directorio donde se extrae y valida la copia subida. */
    public static final String RESTORE_STAGING_PREFIX = "restore-staging-";

    /** Prefijo de las restauraciones pendientes cuya aplicación falló o se descartó. */
    public static final String RESTORE_FAILED_PREFIX = "restore-failed-";

    /**
     * Fichero que deja {@code PendingRestoreApplier} cuando una restauración falla o se
     * descarta, para avisar en Ajustes. Se borra al cancelar o al preparar otra restauración.
     */
    public static final String RESTORE_FAILED_NOTICE = "restore-failed.aviso";

    /** UUID en minúsculas, como los que genera {@link java.util.UUID#randomUUID()}. */
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /**
     * Nombres exactos de los temporales que crean la copia y la restauración: prefijo y UUID
     * (y {@code .zip} en los zips). Cualquier otro fichero con el mismo prefijo no es nuestro.
     */
    public static final Pattern TEMPORARY_NAME = Pattern.compile(
            "^(" + Pattern.quote(BACKUP_TMP_PREFIX) + UUID_REGEX + "(\\.zip)?"
                    + "|" + Pattern.quote(RESTORE_UPLOAD_PREFIX) + UUID_REGEX + "\\.zip"
                    + "|" + Pattern.quote(RESTORE_STAGING_PREFIX) + UUID_REGEX + ")$");

    /**
     * Resuelve las rutas a partir del {@link Environment} efectivo.
     *
     * <p>Usa {@link Binder} con enlace flexible, el mismo mecanismo que
     * {@code @ConfigurationProperties}: así {@code TASKMASTER_STORAGE_AVATARSDIR} y
     * {@code SPRING_DATASOURCE_URL} se resuelven igual que en el resto de la aplicación,
     * también antes de que exista el contexto de Spring.</p>
     *
     * @param environment entorno de Spring ya preparado
     * @return las rutas, o vacío si la base de datos no es un fichero SQLite
     *         (p. ej. {@code :memory:}) o falta alguna de las dos propiedades
     */
    public static Optional<BackupPaths> resolve(Environment environment) {
        Binder binder = Binder.get(environment);
        String url = binder.bind(DATASOURCE_URL_PROPERTY, String.class).orElse(null);
        String avatars = binder.bind(AVATARS_DIR_PROPERTY, String.class).orElse(null);

        Optional<Path> database = databasePath(url);
        if (database.isEmpty() || avatars == null || avatars.isBlank()) {
            return Optional.empty();
        }
        try {
            Path avatarsDir = Path.of(avatars.trim()).toAbsolutePath().normalize();
            return Optional.of(new BackupPaths(database.get(), avatarsDir));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * Extrae la ruta del fichero de una URL JDBC de SQLite.
     *
     * <p>Quita {@code jdbc:sqlite:}, un {@code file:} opcional y los parámetros que vayan
     * tras {@code ?}. Las bases de datos en memoria o en recursos del classpath
     * (las que empiezan por {@code :}) no tienen fichero que copiar.</p>
     *
     * @param url URL JDBC configurada
     * @return la ruta absoluta y normalizada, o vacío si no es un fichero SQLite
     */
    static Optional<Path> databasePath(String url) {
        if (url == null || !url.trim().startsWith(SQLITE_PREFIX)) {
            return Optional.empty();
        }
        String location = url.trim().substring(SQLITE_PREFIX.length());
        if (location.startsWith("file:")) {
            location = location.substring("file:".length());
        }
        int query = location.indexOf('?');
        if (query >= 0) {
            location = location.substring(0, query);
        }
        if (location.isBlank() || location.startsWith(":")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(location).toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** @return directorio que contiene la base de datos y los directorios de trabajo */
    public Path dataDir() {
        return database.getParent();
    }

    /** @return directorio con la restauración pendiente */
    public Path pendingDir() {
        return dataDir().resolve(PENDING_DIR);
    }

    /** @return marca que indica que la restauración pendiente está completa */
    public Path readyMarker() {
        return pendingDir().resolve(READY_MARKER);
    }

    /** @return directorio raíz de los estados anteriores a cada restauración */
    public Path previousRoot() {
        return dataDir().resolve(PREVIOUS_DIR);
    }

    /** @return aviso de que la última restauración no se pudo aplicar */
    public Path failedNotice() {
        return dataDir().resolve(RESTORE_FAILED_NOTICE);
    }

    /**
     * Indica si el directorio de avatares cuelga del directorio de datos (y no es él mismo).
     * Si no, la restauración movería avatares fuera de la carpeta de datos y no se ofrece.
     *
     * @return {@code true} si el directorio de avatares está dentro del de datos
     */
    public boolean avatarsInDataDir() {
        return avatarsDir.startsWith(dataDir()) && !avatarsDir.equals(dataDir());
    }

    /**
     * Borra un fichero o un directorio con todo su contenido, si existe.
     *
     * <p>No sigue enlaces simbólicos: un enlace dentro del árbol se borra a sí mismo,
     * nunca lo que apunta.</p>
     *
     * @param path fichero o directorio que se borra
     * @throws IOException si no se puede borrar algún elemento
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
