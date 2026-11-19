package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.config.BackupPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Aplica, al arrancar el backend, la restauración de una copia de seguridad que
 * {@link com.taskmaster.taskmasterbackend.service.BackupService} dejó preparada en
 * {@code restore-pending/}.
 *
 * <p>Escucha {@link ApplicationEnvironmentPreparedEvent}: el {@code Environment} ya está
 * resuelto (variables de entorno incluidas), pero todavía no existen el contexto, el
 * {@code DataSource} ni {@code AvatarStorageService}. Así la base de datos se sustituye sin
 * conexiones abiertas, y {@code ddl-auto=update} y las limpiezas de arranque se ejecutan
 * después sobre la base de datos restaurada.</p>
 *
 * <p>No es un bean (el contexto aún no existe): se registra a mano en
 * {@link TaskmasterApplication#main}. Por eso los tests que levantan el contexto con
 * {@code @SpringBootTest} no lo ejecutan.</p>
 *
 * <p>Un fallo nunca impide arrancar: se deshacen los movimientos, la copia pendiente se
 * renombra a {@code restore-failed-<marca>} para no reintentarla en bucle y el backend
 * arranca con los datos anteriores.</p>
 *
 * @author Carlos
 */
@Slf4j
public class PendingRestoreApplier implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Número de estados anteriores que se conservan en {@code restore-previous/}. */
    static final int PREVIOUS_TO_KEEP = 3;

    /** Formato de la marca temporal de {@code restore-previous/} y {@code restore-failed-}. */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Sufijos de los ficheros auxiliares de SQLite que acompañan a la base de datos. */
    private static final List<String> DATABASE_SUFFIXES = List.of("", "-journal", "-wal", "-shm");

    /**
     * Resuelve las rutas efectivas y aplica la restauración pendiente, si la hay.
     * Cualquier fallo se registra y no impide el arranque.
     *
     * @param event evento con el {@code Environment} ya preparado
     */
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        try {
            ConfigurableEnvironment environment = event.getEnvironment();
            Optional<BackupPaths> paths = BackupPaths.resolve(environment);
            if (paths.isEmpty()) {
                return;
            }
            String address = environment.getProperty("server.address");
            int port = environment.getProperty("server.port", Integer.class, 8080);
            apply(paths.get(), address, port, Clock.systemDefaultZone());
        } catch (RuntimeException e) {
            // Igual que TaskDependencyCleanup: un fallo aquí no debe impedir que el backend arranque
            log.error("Falló la comprobación de la restauración pendiente al arrancar", e);
        }
    }

    /**
     * Aplica la restauración pendiente sobre las rutas indicadas.
     *
     * @param paths   rutas de la base de datos y de los avatares
     * @param address dirección en la que escuchará el backend ({@code null} o vacía: todas)
     * @param port    puerto en el que escuchará el backend
     * @param clock   reloj para la marca temporal de {@code restore-previous/}
     */
    void apply(BackupPaths paths, String address, int port, Clock clock) {
        Path dataDir = paths.dataDir();
        if (!Files.isDirectory(dataDir)) {
            return;
        }

        // Paso 0: si el puerto está ocupado hay otro backend con la misma BD abierta
        // (segunda instancia o entorno de pruebas). No se toca nada, ni los temporales,
        // que podrían ser de ese otro backend.
        if (!isPortFree(address, port)) {
            if (Files.exists(paths.pendingDir())) {
                log.warn("Otro proceso usa el puerto; la restauración pendiente se aplicará en el próximo arranque");
            }
            return;
        }

        // Paso 1: temporales que dejaron ejecuciones anteriores interrumpidas
        deleteOrphanTemporaries(dataDir);

        // Paso 2: sin la marca LISTA, la pendiente está incompleta y se descarta
        Path pending = paths.pendingDir();
        if (!Files.exists(paths.readyMarker())) {
            if (Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    BackupPaths.deleteRecursively(pending);
                    log.warn("Descartada una restauración pendiente incompleta");
                } catch (IOException e) {
                    log.error("No se pudo borrar una restauración pendiente incompleta", e);
                }
            }
            return;
        }

        String stamp = LocalDateTime.now(clock).format(STAMP);

        // Paso 2b: si después de preparar la restauración se registró otra cuenta, aplicar
        // la copia borraría sus datos; se descarta
        if (countUsers(paths.database()) > 1) {
            Path failed = markFailed(paths, stamp);
            log.warn("Restauración descartada: hay varias cuentas; la copia queda en {}",
                    failed == null ? "restore-pending" : failed.getFileName());
            return;
        }

        Deque<Path[]> moves = new ArrayDeque<>();
        Path previous = null;
        try {
            // Paso 3: estado actual a restore-previous/<marca>/
            previous = uniqueChild(paths.previousRoot(), stamp);
            Files.createDirectories(previous);
            for (String suffix : DATABASE_SUFFIXES) {
                Path current = sibling(paths.database(), suffix);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    move(current, previous.resolve(current.getFileName()), moves);
                }
            }
            if (Files.exists(paths.avatarsDir(), LinkOption.NOFOLLOW_LINKS)) {
                move(paths.avatarsDir(), previous.resolve(BackupPaths.BACKUP_AVATARS_DIR), moves);
            }

            // Paso 4: la copia a su sitio
            Files.createDirectories(paths.database().getParent());
            move(pending.resolve(BackupPaths.BACKUP_DATABASE_ENTRY), paths.database(), moves);
            Path pendingAvatars = pending.resolve(BackupPaths.BACKUP_AVATARS_DIR);
            if (Files.isDirectory(pendingAvatars, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(paths.avatarsDir().getParent());
                move(pendingAvatars, paths.avatarsDir(), moves);
            } else {
                Files.createDirectories(paths.avatarsDir());
            }

            // Sin la marca la pendiente ya no se vuelve a aplicar: es el punto de no retorno
            Files.delete(paths.readyMarker());
        } catch (IOException | RuntimeException e) {
            undo(moves);
            deleteIfEmpty(previous);
            Path failed = markFailed(paths, stamp);
            log.error("Falló la restauración pendiente; se mantienen los datos anteriores y la copia queda en {}",
                    failed == null ? "restore-pending" : failed.getFileName(), e);
            return;
        }

        // Paso 5: limpieza; un fallo aquí ya no deshace la restauración
        try {
            BackupPaths.deleteRecursively(pending);
        } catch (IOException e) {
            log.warn("No se pudo borrar restore-pending tras aplicar la restauración", e);
        }
        pruneOldPrevious(paths.previousRoot());
        log.warn("Restauración aplicada; estado anterior en {}/{}", BackupPaths.PREVIOUS_DIR, previous.getFileName());
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Comprueba si el puerto está libre intentando abrir y cerrar un {@link ServerSocket}.
     *
     * @param address dirección de escucha ({@code null} o vacía: todas)
     * @param port    puerto que se comprueba
     * @return {@code true} si se pudo enlazar el puerto
     */
    private static boolean isPortFree(String address, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            InetSocketAddress endpoint = (address == null || address.isBlank())
                    ? new InetSocketAddress(port)
                    : new InetSocketAddress(InetAddress.getByName(address), port);
            socket.bind(endpoint);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Borra los temporales de copias y restauraciones que quedaron a medias.
     *
     * @param dataDir directorio de datos
     */
    private static void deleteOrphanTemporaries(Path dataDir) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dataDir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.startsWith(BackupPaths.BACKUP_TMP_PREFIX)
                        || name.startsWith(BackupPaths.RESTORE_UPLOAD_PREFIX)
                        || name.startsWith(BackupPaths.RESTORE_STAGING_PREFIX)) {
                    try {
                        BackupPaths.deleteRecursively(entry);
                    } catch (IOException e) {
                        log.warn("No se pudo borrar un temporal de copia de seguridad: {}", name, e);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo revisar el directorio de datos en busca de temporales", e);
        }
    }

    /**
     * Cuenta los usuarios de la base de datos actual, abierta en solo lectura.
     *
     * @param database fichero de la base de datos
     * @return número de usuarios, o 0 si la base de datos no existe, no tiene la tabla
     *         {@code users} o no se puede leer (en esos casos la restauración se aplica)
     */
    private static long countUsers(Path database) {
        if (!Files.isRegularFile(database)) {
            return 0;
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database, config.toProperties());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM users")) {
            return result.next() ? result.getLong(1) : 0;
        } catch (SQLException e) {
            // BD vacía (sin tabla users) o dañada: no hay cuentas que proteger
            return 0;
        }
    }

    /**
     * Mueve un fichero o directorio y apunta el movimiento para poder deshacerlo.
     *
     * <p>Si el directorio no se puede renombrar porque el destino está en otro sistema de
     * ficheros, se copia de forma recursiva y se borra el original.</p>
     *
     * @param source origen
     * @param target destino, que no debe existir
     * @param moves  movimientos hechos hasta ahora, en orden
     * @throws IOException si no se puede mover
     */
    private static void move(Path source, Path target, Deque<Path[]> moves) throws IOException {
        moveOrCopy(source, target);
        moves.push(new Path[]{source, target});
    }

    /**
     * Mueve un fichero o directorio; si es un directorio y el destino está en otro
     * sistema de ficheros, lo copia y borra el original.
     *
     * @param source origen
     * @param target destino, que no debe existir
     * @throws IOException si no se puede mover
     */
    private static void moveOrCopy(Path source, Path target) throws IOException {
        try {
            Files.move(source, target);
        } catch (DirectoryNotEmptyException e) {
            // Files.move no puede mover un directorio con contenido a otro sistema de ficheros
            try {
                copyRecursively(source, target);
            } catch (IOException copyError) {
                BackupPaths.deleteRecursively(target);
                throw copyError;
            }
            BackupPaths.deleteRecursively(source);
        }
    }

    /**
     * Copia un directorio con todo su contenido, sin seguir enlaces simbólicos.
     *
     * @param source directorio de origen
     * @param target directorio de destino, que no debe existir
     * @throws IOException si falla la copia
     */
    private static void copyRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectory(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Deshace los movimientos en orden inverso. Un fallo al deshacer se registra y se
     * sigue con los demás para recuperar todo lo posible.
     *
     * @param moves movimientos hechos, el último en la cima
     */
    private static void undo(Deque<Path[]> moves) {
        while (!moves.isEmpty()) {
            Path[] step = moves.pop();
            try {
                moveOrCopy(step[1], step[0]);
            } catch (IOException e) {
                log.error("No se pudo deshacer un movimiento de la restauración: {} → {}",
                        step[1].getFileName(), step[0].getFileName(), e);
            }
        }
    }

    /**
     * Renombra la restauración pendiente a {@code restore-failed-<marca>} para que no se
     * vuelva a intentar en el siguiente arranque.
     *
     * @param paths rutas de trabajo
     * @param stamp marca temporal
     * @return el directorio renombrado, o {@code null} si no se pudo renombrar
     */
    private static Path markFailed(BackupPaths paths, String stamp) {
        try {
            Path failed = uniqueChild(paths.dataDir(), BackupPaths.RESTORE_FAILED_PREFIX + stamp);
            Files.move(paths.pendingDir(), failed);
            return failed;
        } catch (IOException e) {
            // Último recurso: sin la marca LISTA tampoco se reintenta
            try {
                Files.deleteIfExists(paths.readyMarker());
            } catch (IOException ignored) {
                // Ya se registra el error de abajo
            }
            log.error("No se pudo apartar la restauración pendiente fallida", e);
            return null;
        }
    }

    /**
     * Conserva solo las {@value #PREVIOUS_TO_KEEP} entradas más recientes de
     * {@code restore-previous/} (la marca {@code AAAAMMDD-HHmmss} se ordena por nombre).
     *
     * @param previousRoot directorio {@code restore-previous/}
     */
    private static void pruneOldPrevious(Path previousRoot) {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(previousRoot)) {
            stream.forEach(entries::add);
        } catch (IOException e) {
            log.warn("No se pudo revisar restore-previous", e);
            return;
        }
        entries.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        for (Path old : entries.subList(Math.min(PREVIOUS_TO_KEEP, entries.size()), entries.size())) {
            try {
                BackupPaths.deleteRecursively(old);
            } catch (IOException e) {
                log.warn("No se pudo borrar un estado anterior antiguo: {}", old.getFileName(), e);
            }
        }
    }

    /**
     * Devuelve un hijo de {@code parent} con el nombre indicado que todavía no exista,
     * añadiendo {@code -1}, {@code -2}… si hace falta (dos restauraciones en el mismo segundo).
     */
    private static Path uniqueChild(Path parent, String name) {
        Path candidate = parent.resolve(name);
        for (int i = 1; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); i++) {
            candidate = parent.resolve(name + "-" + i);
        }
        return candidate;
    }

    /** Ruta hermana de la base de datos con el sufijo indicado ({@code -journal}, etc.). */
    private static Path sibling(Path database, String suffix) {
        return database.resolveSibling(database.getFileName() + suffix);
    }

    /** Borra un directorio si existe y está vacío; los fallos se ignoran. */
    private static void deleteIfEmpty(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // No está vacío o no se puede borrar: se deja para no perder nada
        }
    }
}
