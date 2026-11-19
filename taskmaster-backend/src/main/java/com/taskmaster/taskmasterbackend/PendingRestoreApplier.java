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
import java.util.regex.Pattern;

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
 * renombra a {@code restore-failed-<marca>} para no reintentarla en bucle, se deja el aviso
 * {@code restore-failed.aviso} para Ajustes y el backend arranca con los datos anteriores.</p>
 *
 * @author Carlos
 */
@Slf4j
public class PendingRestoreApplier implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Número de estados anteriores que se conservan en {@code restore-previous/}. */
    static final int PREVIOUS_TO_KEEP = 3;

    /** Número de restauraciones fallidas ({@code restore-failed-<marca>}) que se conservan. */
    static final int FAILED_TO_KEEP = 3;

    /** Nombre de las restauraciones fallidas: prefijo, marca y un {@code -n} opcional. */
    private static final Pattern FAILED_NAME = Pattern.compile(
            "^" + Pattern.quote(BackupPaths.RESTORE_FAILED_PREFIX) + "\\d{8}-\\d{6}(-\\d+)?$");

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

        // Con los avatares fuera del directorio de datos, mover y guardar el estado anterior
        // tocaría otra carpeta: no se aplica (BackupService tampoco ofrece la función)
        if (!paths.avatarsInDataDir()) {
            if (Files.exists(paths.pendingDir(), LinkOption.NOFOLLOW_LINKS)) {
                log.warn("El directorio de avatares no está dentro del de datos; la restauración pendiente no se aplica");
            }
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
        // la copia borraría sus datos; se descarta. También si no se puede saber cuántas hay
        long users;
        try {
            users = countUsers(paths.database());
        } catch (SQLException e) {
            Path failed = markFailed(paths, stamp);
            log.warn("Restauración descartada: no se pudo leer la base de datos actual; la copia queda en {}",
                    failed == null ? "restore-pending" : failed.getFileName(), e);
            return;
        }
        if (users > 1) {
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
                // Se apunta como paso deshacible: si algo falla después, el directorio vacío
                // se borra antes de devolver los avatares originales a su sitio
                Files.createDirectories(paths.avatarsDir());
                moves.push(new Path[]{null, paths.avatarsDir()});
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
                // Solo los nombres exactos que generan BackupService (prefijo y UUID)
                if (BackupPaths.TEMPORARY_NAME.matcher(name).matches()) {
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
     * @return número de usuarios, o 0 si la base de datos no existe o no tiene la tabla
     *         {@code users} (en esos casos no hay cuentas que proteger y se aplica)
     * @throws SQLException ante cualquier otro error (fichero dañado, bloqueado, que no es
     *                      SQLite…): no se sabe cuántas cuentas hay y la restauración se descarta
     */
    private static long countUsers(Path database) throws SQLException {
        if (!Files.exists(database, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database, config.toProperties());
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'users'")) {
                if (!result.next() || result.getLong(1) == 0) {
                    return 0;
                }
            }
            try (ResultSet result = statement.executeQuery("SELECT count(*) FROM users")) {
                return result.next() ? result.getLong(1) : 0;
            }
        }
    }

    /**
     * Mueve un fichero o directorio y apunta el movimiento para poder deshacerlo.
     *
     * <p>Si el directorio no se puede renombrar porque el destino está en otro sistema de
     * ficheros, se copia de forma recursiva y se borra el original. El movimiento se apunta
     * en cuanto la copia está completa, antes de borrar el original: si ese borrado falla a
     * medias, {@link #undo} sigue teniendo la copia entera para completar el original.</p>
     *
     * @param source origen
     * @param target destino, que no debe existir
     * @param moves  movimientos hechos hasta ahora, en orden
     * @throws IOException si no se puede mover
     */
    private static void move(Path source, Path target, Deque<Path[]> moves) throws IOException {
        moveOrCopy(source, target, () -> moves.push(new Path[]{source, target}));
    }

    /**
     * Mueve un fichero o directorio; si es un directorio y el destino está en otro
     * sistema de ficheros, lo copia y borra el original.
     *
     * @param source   origen
     * @param target   destino, que no debe existir
     * @param onPlaced se ejecuta en cuanto el destino está completo (antes de borrar el
     *                 original, si hubo que copiar)
     * @throws IOException si no se puede mover
     */
    private static void moveOrCopy(Path source, Path target, Runnable onPlaced) throws IOException {
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
            onPlaced.run();
            BackupPaths.deleteRecursively(source);
            return;
        }
        onPlaced.run();
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
     * <p>Cada paso es {@code {origen, destino}}. Un origen {@code null} indica un directorio
     * creado vacío, que se borra (solo si sigue vacío). Si el origen todavía existe, es un
     * movimiento entre sistemas de ficheros cuyo borrado del original se quedó a medias: se
     * le devuelve lo que le falta desde la copia completa y después se borra la copia.</p>
     *
     * @param moves movimientos hechos, el último en la cima
     */
    static void undo(Deque<Path[]> moves) {
        while (!moves.isEmpty()) {
            Path[] step = moves.pop();
            try {
                if (step[0] == null) {
                    Files.deleteIfExists(step[1]);
                } else if (Files.exists(step[0], LinkOption.NOFOLLOW_LINKS)) {
                    copyMissing(step[1], step[0]);
                    BackupPaths.deleteRecursively(step[1]);
                } else {
                    moveOrCopy(step[1], step[0], () -> { });
                }
            } catch (IOException e) {
                log.error("No se pudo deshacer un paso de la restauración: {}", step[1].getFileName(), e);
            }
        }
    }

    /**
     * Copia a {@code target} lo que tiene {@code source} y a {@code target} le falta, sin
     * sobrescribir nada ni seguir enlaces simbólicos. Completa un directorio original cuyo
     * borrado se quedó a medias tras copiarlo entero a otro sistema de ficheros.
     *
     * @param source copia completa
     * @param target directorio incompleto
     * @throws IOException si falla la copia o {@code target} no es un directorio
     */
    private static void copyMissing(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("No se puede completar " + target.getFileName() + ": ya existe y no es un directorio");
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path copy = target.resolve(source.relativize(dir).toString());
                if (!Files.exists(copy, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(copy);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path copy = target.resolve(source.relativize(file).toString());
                if (!Files.exists(copy, LinkOption.NOFOLLOW_LINKS)) {
                    Files.copy(file, copy, LinkOption.NOFOLLOW_LINKS);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Renombra la restauración pendiente a {@code restore-failed-<marca>} para que no se
     * vuelva a intentar en el siguiente arranque, deja el aviso para Ajustes y conserva solo
     * las {@value #FAILED_TO_KEEP} restauraciones fallidas más recientes.
     *
     * @param paths rutas de trabajo
     * @param stamp marca temporal
     * @return el directorio renombrado, o {@code null} si no se pudo renombrar
     */
    private static Path markFailed(BackupPaths paths, String stamp) {
        try {
            Files.writeString(paths.failedNotice(), stamp);
        } catch (IOException e) {
            log.warn("No se pudo dejar el aviso de restauración fallida", e);
        }
        Path failed = moveToFailed(paths, stamp);
        pruneOldFailed(paths.dataDir());
        return failed;
    }

    /**
     * Renombra {@code restore-pending/} a {@code restore-failed-<marca>}.
     *
     * @return el directorio renombrado, o {@code null} si no se pudo renombrar
     */
    private static Path moveToFailed(BackupPaths paths, String stamp) {
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
        deleteAllButNewest(entries, PREVIOUS_TO_KEEP);
    }

    /**
     * Conserva solo las {@value #FAILED_TO_KEEP} restauraciones fallidas más recientes
     * ({@code restore-failed-<marca>}); no toca nada con otro nombre.
     *
     * @param dataDir directorio de datos
     */
    private static void pruneOldFailed(Path dataDir) {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir)) {
            for (Path entry : stream) {
                if (FAILED_NAME.matcher(entry.getFileName().toString()).matches()
                        && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo revisar el directorio de datos en busca de restauraciones fallidas", e);
            return;
        }
        deleteAllButNewest(entries, FAILED_TO_KEEP);
    }

    /**
     * Borra todas las entradas salvo las {@code keep} más recientes; la marca
     * {@code AAAAMMDD-HHmmss} del nombre se ordena como texto.
     *
     * @param entries entradas candidatas
     * @param keep    número de entradas que se conservan
     */
    private static void deleteAllButNewest(List<Path> entries, int keep) {
        entries.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        for (Path old : entries.subList(Math.min(keep, entries.size()), entries.size())) {
            try {
                BackupPaths.deleteRecursively(old);
            } catch (IOException e) {
                log.warn("No se pudo borrar una copia antigua de la restauración: {}", old.getFileName(), e);
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
