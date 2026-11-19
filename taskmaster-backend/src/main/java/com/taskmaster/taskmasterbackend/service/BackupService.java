package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.config.BackupPaths;
import com.taskmaster.taskmasterbackend.dto.response.BackupStatusResponse;
import com.taskmaster.taskmasterbackend.dto.response.RestoreStagedResponse;
import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.sqlite.SQLiteConfig;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Servicio que crea copias de seguridad de los datos de TaskMaster y prepara su restauración.
 *
 * <p>La copia es un zip con {@code manifest.properties}, {@code taskmaster.db} (instantánea
 * consistente de la base de datos en uso, hecha con {@code VACUUM INTO}) y los avatares
 * ({@code avatars/<uuid>.png|jpg}). No incluye {@code config.properties} ni {@code startup.log},
 * que son del equipo y no de los datos.</p>
 *
 * <p>La base de datos es compartida por todas las cuentas del equipo, así que crear y
 * restaurar solo se permite cuando hay una única cuenta: la copia solo contiene datos
 * propios y la restauración no sustituye los de nadie más.</p>
 *
 * <p>La restauración no se aplica en caliente: aquí solo se valida la copia y se deja en
 * {@code restore-pending/}; la aplica
 * {@link com.taskmaster.taskmasterbackend.PendingRestoreApplier} en el siguiente arranque.</p>
 *
 * @author Carlos
 */
@Slf4j
@Service
public class BackupService {

    /** Tamaño máximo del zip subido (256 MB). */
    static final long MAX_UPLOAD_BYTES = 256L * 1024 * 1024;

    /** Tamaño máximo de la base de datos descomprimida (256 MB). */
    static final long MAX_DB_BYTES = 256L * 1024 * 1024;

    /** Tamaño máximo de cada avatar, igual que {@code AvatarService.MAX_SIZE_BYTES} (2 MB). */
    static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    /** Número máximo de entradas del zip. */
    static final int MAX_ENTRIES = 10_000;

    /** Tamaño máximo descomprimido de toda la copia (512 MB), contra bombas zip. */
    static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;

    /** Versión del formato de la copia, guardada en el manifiesto. */
    static final String FORMAT = "1";

    /** Nombre del manifiesto dentro de la copia. */
    static final String MANIFEST_ENTRY = "manifest.properties";

    /** Nombres de avatar que genera {@code AvatarStorageService.save}: UUID y extensión. */
    static final Pattern AVATAR_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)$");

    /** Cabecera de los ficheros de base de datos SQLite. */
    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    /** Tablas que debe tener la base de datos de la copia (las demás las crea {@code ddl-auto}). */
    private static final List<String> REQUIRED_TABLES = List.of("users", "projects", "tasks");

    private static final String UNAVAILABLE_MESSAGE =
            "La copia de seguridad solo está disponible cuando hay una única cuenta en este equipo";
    private static final String INVALID_BACKUP_MESSAGE = "El archivo no es una copia de seguridad de TaskMaster";
    private static final String INCOMPATIBLE_MESSAGE = "Copia de una versión no compatible";
    private static final String TOO_LARGE_MESSAGE = "La copia de seguridad supera el tamaño máximo";

    private final DataSource dataSource;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Rutas efectivas, o {@code null} si la base de datos no es un fichero SQLite. */
    private final BackupPaths paths;

    private final long maxUploadBytes;
    private final long maxDbBytes;
    private final long maxAvatarBytes;
    private final long maxTotalBytes;

    /** Evita que dos peticiones preparen o cancelen la restauración a la vez. */
    private final ReentrantLock restoreLock = new ReentrantLock();

    /**
     * Constructor que usa Spring, con los límites de tamaño por defecto.
     *
     * @param dataSource      origen de datos de la aplicación (para {@code VACUUM INTO})
     * @param userRepository  repositorio de usuarios
     * @param passwordEncoder codificador para comprobar la contraseña al restaurar
     * @param environment     entorno de Spring, del que se deducen las rutas de datos
     */
    @Autowired
    public BackupService(DataSource dataSource, UserRepository userRepository,
                         PasswordEncoder passwordEncoder, Environment environment) {
        this(dataSource, userRepository, passwordEncoder, environment,
                MAX_UPLOAD_BYTES, MAX_DB_BYTES, MAX_AVATAR_BYTES, MAX_TOTAL_BYTES);
    }

    /**
     * Constructor con límites de tamaño a medida, para los tests.
     */
    BackupService(DataSource dataSource, UserRepository userRepository, PasswordEncoder passwordEncoder,
                  Environment environment, long maxUploadBytes, long maxDbBytes,
                  long maxAvatarBytes, long maxTotalBytes) {
        this.dataSource = dataSource;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.paths = BackupPaths.resolve(environment).orElse(null);
        this.maxUploadBytes = maxUploadBytes;
        this.maxDbBytes = maxDbBytes;
        this.maxAvatarBytes = maxAvatarBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    // -------------------------------------------------------------------------
    // Estado
    // -------------------------------------------------------------------------

    /**
     * Devuelve si la copia de seguridad está disponible y si hay una restauración pendiente.
     *
     * @return estado de la copia de seguridad
     */
    public BackupStatusResponse getStatus() {
        boolean pending = paths != null && Files.exists(paths.readyMarker());
        return new BackupStatusResponse(isAvailable(), pending);
    }

    // -------------------------------------------------------------------------
    // Crear copia
    // -------------------------------------------------------------------------

    /**
     * Crea una copia de seguridad en un zip temporal del directorio de datos.
     *
     * <p>El llamante es responsable de borrar el zip cuando termine de enviarlo; los
     * restos que queden los borra {@code PendingRestoreApplier} al arrancar.</p>
     *
     * @param userId identificador del usuario que la pide
     * @return ruta del zip creado
     * @throws BusinessException si la copia no está disponible (varias cuentas)
     */
    public Path createBackup(Long userId) {
        ensureAvailable();

        String id = UUID.randomUUID().toString();
        Path workDir = paths.dataDir().resolve(BackupPaths.BACKUP_TMP_PREFIX + id);
        Path zip = paths.dataDir().resolve(BackupPaths.BACKUP_TMP_PREFIX + id + ".zip");
        boolean ok = false;
        try {
            Files.createDirectories(workDir);
            Path snapshot = workDir.resolve(BackupPaths.BACKUP_DATABASE_ENTRY);
            vacuumInto(snapshot);
            writeZip(snapshot, zip);
            ok = true;
            log.info("Copia de seguridad creada para usuario id: {} ({} bytes)", userId, Files.size(zip));
            return zip;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la copia de seguridad", e);
        } finally {
            deleteQuietly(workDir);
            if (!ok) {
                deleteQuietly(zip);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Preparar y cancelar la restauración
    // -------------------------------------------------------------------------

    /**
     * Valida una copia de seguridad y la deja en {@code restore-pending/} para que se
     * aplique en el siguiente arranque. La base de datos en uso no se toca.
     *
     * <p>Si la copia no es válida no queda ningún fichero: se borran el zip subido y
     * el directorio de extracción.</p>
     *
     * @param userId   identificador del usuario que la pide
     * @param password contraseña actual, como confirmación
     * @param body     contenido del zip
     * @return usuarios que contiene la copia
     * @throws BusinessException       si la contraseña no es correcta, hay varias cuentas
     *                                 o el fichero no es una copia válida
     * @throws ResponseStatusException 413 si el zip supera el tamaño máximo
     */
    public RestoreStagedResponse stageRestore(Long userId, String password, InputStream body) {
        restoreLock.lock();
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));
            if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
                throw new BusinessException("La contraseña no es correcta");
            }
            ensureAvailable();

            String id = UUID.randomUUID().toString();
            Path upload = paths.dataDir().resolve(BackupPaths.RESTORE_UPLOAD_PREFIX + id + ".zip");
            Path staging = paths.dataDir().resolve(BackupPaths.RESTORE_STAGING_PREFIX + id);
            boolean staged = false;
            try {
                copyLimited(body, upload, maxUploadBytes, () ->
                        new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE_MESSAGE));
                extract(upload, staging);
                checkManifest(staging.resolve(MANIFEST_ENTRY));
                List<String> usernames = validateDatabase(staging.resolve(BackupPaths.BACKUP_DATABASE_ENTRY));

                Files.writeString(staging.resolve(BackupPaths.READY_MARKER), Instant.now().toString());
                BackupPaths.deleteRecursively(paths.pendingDir());
                Files.move(staging, paths.pendingDir(), StandardCopyOption.ATOMIC_MOVE);
                staged = true;

                log.info("Restauración preparada para usuario id: {}; se aplicará al reiniciar", userId);
                return new RestoreStagedResponse(usernames);
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo preparar la restauración", e);
            } finally {
                deleteQuietly(upload);
                if (!staged) {
                    deleteQuietly(staging);
                }
            }
        } finally {
            restoreLock.unlock();
        }
    }

    /**
     * Cancela la restauración pendiente, si la hay.
     *
     * @param userId identificador del usuario que la pide
     * @throws BusinessException si la copia no está disponible (varias cuentas)
     */
    public void cancelPendingRestore(Long userId) {
        ensureAvailable();
        restoreLock.lock();
        try {
            BackupPaths.deleteRecursively(paths.pendingDir());
            log.info("Restauración pendiente cancelada por usuario id: {}", userId);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cancelar la restauración pendiente", e);
        } finally {
            restoreLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados: disponibilidad
    // -------------------------------------------------------------------------

    /** @return {@code true} si se conocen las rutas y la base de datos tiene una única cuenta */
    private boolean isAvailable() {
        return paths != null && userRepository.count() == 1;
    }

    /** @throws BusinessException si la copia de seguridad no está disponible */
    private void ensureAvailable() {
        if (!isAvailable()) {
            throw new BusinessException(UNAVAILABLE_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados: crear copia
    // -------------------------------------------------------------------------

    /**
     * Hace una instantánea consistente de la base de datos en uso con {@code VACUUM INTO}.
     *
     * <p>{@code VACUUM} no puede ir dentro de una transacción: por eso usa una conexión
     * propia en modo autocommit y este servicio no es {@code @Transactional}. La ruta
     * la genera el backend (directorio temporal con UUID).</p>
     *
     * @param target fichero de destino, que no debe existir
     */
    private void vacuumInto(Path target) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            if (!autoCommit) {
                connection.setAutoCommit(true);
            }
            try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, target.toString());
                statement.execute();
            } finally {
                if (!autoCommit) {
                    connection.setAutoCommit(false);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo copiar la base de datos", e);
        }
    }

    /**
     * Escribe el zip de la copia: manifiesto, base de datos y avatares.
     *
     * @param snapshot instantánea de la base de datos
     * @param zip      fichero zip de destino
     * @throws IOException si falla la escritura
     */
    private void writeZip(Path snapshot, Path zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            String manifest = "formato=" + FORMAT + "\ncreado=" + Instant.now() + "\n";
            out.write(manifest.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();

            out.putNextEntry(new ZipEntry(BackupPaths.BACKUP_DATABASE_ENTRY));
            Files.copy(snapshot, out);
            out.closeEntry();

            out.putNextEntry(new ZipEntry(BackupPaths.BACKUP_AVATARS_DIR + "/"));
            out.closeEntry();
            for (Path avatar : listAvatars()) {
                out.putNextEntry(new ZipEntry(BackupPaths.BACKUP_AVATARS_DIR + "/" + avatar.getFileName()));
                Files.copy(avatar, out);
                out.closeEntry();
            }
        }
    }

    /**
     * Lista los avatares del directorio de avatares cuyo nombre sigue el formato de
     * {@code AvatarStorageService}; cualquier otro fichero se ignora.
     *
     * @return ficheros de avatar, ordenados por nombre
     * @throws IOException si no se puede leer el directorio
     */
    private List<Path> listAvatars() throws IOException {
        List<Path> avatars = new ArrayList<>();
        if (!Files.isDirectory(paths.avatarsDir())) {
            return avatars;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.avatarsDir())) {
            for (Path file : stream) {
                if (AVATAR_NAME.matcher(file.getFileName().toString()).matches()
                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    avatars.add(file);
                }
            }
        }
        avatars.sort(null);
        return avatars;
    }

    // -------------------------------------------------------------------------
    // Métodos privados: validar la copia
    // -------------------------------------------------------------------------

    /**
     * Comprueba los nombres de todas las entradas del zip y, si todos son válidos,
     * las extrae al directorio de staging contando los bytes reales.
     *
     * @param upload  zip subido
     * @param staging directorio de extracción, que no debe existir
     * @throws BusinessException si el fichero no es un zip, tiene entradas no permitidas
     *                           o supera algún límite de tamaño
     * @throws IOException       si falla la lectura o la escritura
     */
    private void extract(Path upload, Path staging) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(upload.toFile(), StandardCharsets.UTF_8);
        } catch (ZipException e) {
            throw new BusinessException(INVALID_BACKUP_MESSAGE);
        }
        try (zip) {
            if (zip.size() > MAX_ENTRIES) {
                throw new BusinessException(INVALID_BACKUP_MESSAGE);
            }

            // Primero se comprueban todos los nombres, para no extraer nada de un zip no válido
            List<ZipEntry> entries = new ArrayList<>();
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                String name = entry.getName();
                if (!isAllowedName(name) || !names.add(name)) {
                    throw new BusinessException(INVALID_BACKUP_MESSAGE);
                }
                entries.add(entry);
            }
            if (!names.contains(BackupPaths.BACKUP_DATABASE_ENTRY)) {
                throw new BusinessException(INVALID_BACKUP_MESSAGE);
            }

            Files.createDirectory(staging);
            long total = 0;
            for (ZipEntry entry : entries) {
                Path target = staging.resolve(entry.getName()).normalize();
                // Defensa adicional contra zip-slip, aunque la lista blanca ya lo impide
                if (!target.startsWith(staging)) {
                    throw new BusinessException(INVALID_BACKUP_MESSAGE);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                // La BD tiene su límite; el manifiesto y cada avatar, el de un avatar
                long limit = entry.getName().equals(BackupPaths.BACKUP_DATABASE_ENTRY) ? maxDbBytes : maxAvatarBytes;
                long remaining = maxTotalBytes - total;
                try (InputStream in = zip.getInputStream(entry)) {
                    total += copyLimited(in, target, Math.min(limit, remaining),
                            () -> new BusinessException(TOO_LARGE_MESSAGE));
                } catch (ZipException e) {
                    throw new BusinessException(INVALID_BACKUP_MESSAGE);
                }
            }
        } catch (IllegalArgumentException e) {
            // Nombres de entrada con codificación no válida
            throw new BusinessException(INVALID_BACKUP_MESSAGE);
        }
    }

    /**
     * Lista blanca de nombres de entrada: {@code manifest.properties}, {@code taskmaster.db},
     * {@code avatars/} y {@code avatars/<uuid>.png|jpg}. Rechaza además, de forma explícita,
     * barras invertidas, rutas absolutas y segmentos {@code ..}.
     *
     * @param name nombre de la entrada del zip
     * @return {@code true} si el nombre está permitido
     */
    static boolean isAllowedName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\\') >= 0 || name.startsWith("/")
                || name.indexOf(':') >= 0 || Arrays.asList(name.split("/")).contains("..")) {
            return false;
        }
        if (name.equals(MANIFEST_ENTRY) || name.equals(BackupPaths.BACKUP_DATABASE_ENTRY)
                || name.equals(BackupPaths.BACKUP_AVATARS_DIR + "/")) {
            return true;
        }
        String prefix = BackupPaths.BACKUP_AVATARS_DIR + "/";
        return name.startsWith(prefix) && AVATAR_NAME.matcher(name.substring(prefix.length())).matches();
    }

    /**
     * Comprueba que el manifiesto existe y declara el formato que entiende esta versión.
     *
     * @param manifest fichero {@code manifest.properties} extraído
     * @throws BusinessException si falta o el formato no es compatible
     * @throws IOException       si falla la lectura
     */
    private static void checkManifest(Path manifest) throws IOException {
        if (!Files.isRegularFile(manifest)) {
            throw new BusinessException(INCOMPATIBLE_MESSAGE);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException | CharacterCodingException e) {
            throw new BusinessException(INCOMPATIBLE_MESSAGE);
        }
        if (!FORMAT.equals(properties.getProperty("formato"))) {
            throw new BusinessException(INCOMPATIBLE_MESSAGE);
        }
    }

    /**
     * Valida que la base de datos de la copia es una base de datos SQLite íntegra de
     * TaskMaster y devuelve sus usuarios.
     *
     * <p>Se abre en solo lectura y con {@code trusted_schema} desactivado. Se rechazan las
     * bases de datos con triggers o vistas: Hibernate no crea ninguno y un trigger
     * ejecutaría SQL ajeno en cada escritura.</p>
     *
     * @param database fichero extraído
     * @return nombres de usuario, en orden alfabético
     * @throws BusinessException si no es una base de datos válida de TaskMaster
     * @throws IOException       si falla la lectura de la cabecera
     */
    private static List<String> validateDatabase(Path database) throws IOException {
        byte[] header = new byte[SQLITE_HEADER.length];
        try (InputStream in = Files.newInputStream(database)) {
            if (in.readNBytes(header, 0, header.length) != header.length || !Arrays.equals(header, SQLITE_HEADER)) {
                throw new BusinessException(INVALID_BACKUP_MESSAGE);
            }
        }

        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database, config.toProperties());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA trusted_schema = OFF");

            try (ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                if (!result.next() || !"ok".equals(result.getString(1)) || result.next()) {
                    throw new BusinessException(INVALID_BACKUP_MESSAGE);
                }
            }

            Set<String> tables = new HashSet<>();
            try (ResultSet result = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
            if (!tables.containsAll(REQUIRED_TABLES)) {
                throw new BusinessException(INVALID_BACKUP_MESSAGE);
            }

            Set<String> userColumns = new HashSet<>();
            try (ResultSet result = statement.executeQuery("PRAGMA table_info(users)")) {
                while (result.next()) {
                    userColumns.add(result.getString("name"));
                }
            }
            if (!userColumns.containsAll(List.of("username", "password"))) {
                throw new BusinessException(INVALID_BACKUP_MESSAGE);
            }

            try (ResultSet result = statement.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type IN ('trigger', 'view')")) {
                if (!result.next() || result.getLong(1) != 0) {
                    throw new BusinessException(INVALID_BACKUP_MESSAGE);
                }
            }

            List<String> usernames = new ArrayList<>();
            try (ResultSet result = statement.executeQuery("SELECT username FROM users ORDER BY username")) {
                while (result.next()) {
                    usernames.add(result.getString(1));
                }
            }
            if (usernames.isEmpty()) {
                throw new BusinessException(INVALID_BACKUP_MESSAGE);
            }
            return usernames;
        } catch (SQLException e) {
            throw new BusinessException(INVALID_BACKUP_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados: ficheros
    // -------------------------------------------------------------------------

    /**
     * Copia un flujo a un fichero nuevo cortando en cuanto supera el límite.
     * No se fía de los tamaños que declare el zip: cuenta los bytes reales.
     *
     * @param in       flujo de origen
     * @param target   fichero de destino, que no debe existir
     * @param limit    número máximo de bytes
     * @param tooLarge excepción que se lanza si se supera el límite
     * @return bytes copiados
     * @throws IOException si falla la lectura o la escritura
     */
    private static long copyLimited(InputStream in, Path target, long limit,
                                    Supplier<? extends RuntimeException> tooLarge)
            throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw tooLarge.get();
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    /** Borra un fichero o directorio sin propagar errores (los restos los limpia el arranque). */
    private static void deleteQuietly(Path path) {
        try {
            BackupPaths.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("No se pudo borrar un temporal de copia de seguridad: {}", path.getFileName(), e);
        }
    }
}
