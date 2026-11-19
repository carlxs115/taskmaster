package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.config.BackupPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import static com.taskmaster.taskmasterbackend.BackupTestSupport.createDatabase;
import static com.taskmaster.taskmasterbackend.BackupTestSupport.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests de {@link PendingRestoreApplier} (FUN-1): aplicación al arrancar de la restauración
 * pendiente, vuelta atrás si falla y limpieza de temporales.
 *
 * <p>Sin Spring: se llama directamente a {@code apply} con rutas dentro de {@code @TempDir},
 * un reloj fijo y el puerto 0 (siempre libre) salvo en el test del puerto ocupado. La base
 * de datos «actual» es una SQLite real con una cuenta: una que no se puede leer descarta la
 * restauración.</p>
 *
 * @author Carlos
 */
class PendingRestoreApplierTest {

    /** Reloj fijo: la marca de {@code restore-previous/} es {@value #STAMP}. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T10:15:30Z"), ZoneOffset.UTC);
    private static final String STAMP = "20261119-101530";
    private static final String UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    @TempDir
    Path root;

    private Path dataDir;
    private Path database;
    private Path avatarsDir;
    private Path pending;
    private Path notice;
    private BackupPaths paths;
    private byte[] oldDatabase;
    private final PendingRestoreApplier applier = new PendingRestoreApplier();

    @BeforeEach
    void setUp() throws Exception {
        dataDir = root.resolve("datos");
        database = dataDir.resolve("taskmaster.db");
        avatarsDir = dataDir.resolve("avatars");
        pending = dataDir.resolve("restore-pending");
        notice = dataDir.resolve("restore-failed.aviso");
        Files.createDirectories(avatarsDir);
        createDatabase(database, "carlos");
        oldDatabase = Files.readAllBytes(database);
        Files.writeString(avatarsDir.resolve("a.png"), "avatar viejo");
        paths = new BackupPaths(database, avatarsDir);
    }

    @Test
    void sinPendienteNoTocaNada() throws IOException {
        Files.writeString(dataDir.resolve("config.properties"), "language=es");
        Set<String> before = tree(root);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(tree(root)).isEqualTo(before);
        assertOldDatabase(database);
    }

    @Test
    void puertoOcupadoNoAplicaNiBorraTemporales() throws IOException {
        preparePending(true);
        Files.createDirectories(dataDir.resolve("backup-tmp-x"));
        Files.writeString(dataDir.resolve("restore-upload-y.zip"), "subida");
        Set<String> before = tree(root);

        try (ServerSocket busy = new ServerSocket()) {
            busy.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            applier.apply(paths, busy.getInetAddress().getHostAddress(), busy.getLocalPort(), CLOCK);
        }

        assertThat(tree(root)).isEqualTo(before);
        assertOldDatabase(database);
    }

    @Test
    void aplicaLaPendienteYGuardaElEstadoAnterior() throws IOException {
        // Journal con la cabecera a cero: SQLite no lo considera caliente y lo ignora,
        // pero se guarda con la BD en restore-previous
        byte[] journal = new byte[512];
        journal[100] = 7;
        Files.write(dataDir.resolve("taskmaster.db-journal"), journal);
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        Path previous = dataDir.resolve("restore-previous").resolve(STAMP);
        assertThat(database).hasContent("nueva");
        assertThat(dataDir.resolve("taskmaster.db-journal")).doesNotExist();
        assertThat(tree(avatarsDir)).containsExactly("b.png");
        assertOldDatabase(previous.resolve("taskmaster.db"));
        assertThat(Files.readAllBytes(previous.resolve("taskmaster.db-journal"))).isEqualTo(journal);
        assertThat(previous.resolve("avatars/a.png")).hasContent("avatar viejo");
        assertThat(pending).doesNotExist();
        assertThat(notice).doesNotExist();
    }

    @Test
    void pendienteSinAvataresDejaElDirectorioVacio() throws IOException {
        Files.createDirectories(pending);
        Files.writeString(pending.resolve("taskmaster.db"), "nueva");
        Files.writeString(pending.resolve("LISTA"), "");

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(database).hasContent("nueva");
        assertThat(avatarsDir).isEmptyDirectory();
        assertThat(pending).doesNotExist();
    }

    @Test
    void pendienteSinMarcaSeDescartaSinTocarLaBd() throws IOException {
        preparePending(false);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(pending).doesNotExist();
        assertOldDatabase(database);
        assertThat(avatarsDir.resolve("a.png")).exists();
        assertThat(dataDir.resolve("restore-previous")).doesNotExist();
    }

    @Test
    void falloAMitadDeshaceYRenombraLaPendiente() throws IOException {
        // El padre del directorio de avatares es un fichero: la BD se mueve bien,
        // pero colocar los avatares de la copia falla y hay que deshacer
        Files.writeString(dataDir.resolve("bloqueo"), "soy un fichero");
        Path blockedAvatars = dataDir.resolve("bloqueo/avatars");
        BackupPaths blocked = new BackupPaths(database, blockedAvatars);
        preparePending(true);

        assertThatCode(() -> applier.apply(blocked, "127.0.0.1", 0, CLOCK)).doesNotThrowAnyException();

        Path failed = dataDir.resolve("restore-failed-" + STAMP);
        assertOldDatabase(database);
        assertThat(pending).doesNotExist();
        assertThat(failed.resolve("taskmaster.db")).hasContent("nueva");
        assertThat(failed.resolve("avatars/b.png")).exists();
        assertThat(dataDir.resolve("restore-previous").resolve(STAMP)).doesNotExist();
        assertThat(notice).exists();

        // No se reintenta en bucle: el siguiente arranque no hace nada
        applier.apply(blocked, "127.0.0.1", 0, CLOCK);
        assertOldDatabase(database);
        assertThat(failed).exists();
    }

    @Test
    void conservaSoloLasTresUltimasCopiasAnteriores() throws IOException {
        Path previousRoot = dataDir.resolve("restore-previous");
        for (String old : new String[]{"20200101-000000", "20210101-000000", "20220101-000000", "20230101-000000"}) {
            Files.createDirectories(previousRoot.resolve(old));
            Files.writeString(previousRoot.resolve(old).resolve("taskmaster.db"), old);
        }
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(previousRoot).isDirectoryContaining(p -> p.getFileName().toString().equals(STAMP));
        try (var entries = Files.list(previousRoot)) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder(STAMP, "20230101-000000", "20220101-000000");
        }
    }

    @Test
    void variasCuentasEnLaBdActualDescartaLaPendiente() throws Exception {
        Files.delete(database);
        createDatabase(database, "carlos", "otra");
        byte[] before = Files.readAllBytes(database);
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(Files.readAllBytes(database)).isEqualTo(before);
        assertThat(avatarsDir.resolve("a.png")).exists();
        assertThat(pending).doesNotExist();
        assertThat(dataDir.resolve("restore-failed-" + STAMP).resolve("taskmaster.db")).hasContent("nueva");
        assertThat(dataDir.resolve("restore-previous")).doesNotExist();
        assertThat(notice).exists();
    }

    @Test
    void bdActualIlegibleDescartaLaPendiente() throws Exception {
        // No es la falta de la tabla users: no se sabe cuántas cuentas hay y no se arriesga
        Files.writeString(database, "esto no es una base de datos SQLite, aunque lo parezca por el nombre");
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(database).hasContent("esto no es una base de datos SQLite, aunque lo parezca por el nombre");
        assertThat(avatarsDir.resolve("a.png")).exists();
        assertThat(pending).doesNotExist();
        assertThat(dataDir.resolve("restore-failed-" + STAMP).resolve("taskmaster.db")).hasContent("nueva");
        assertThat(dataDir.resolve("restore-previous")).doesNotExist();
        assertThat(notice).exists();
    }

    @Test
    void bdActualConJournalCalienteDescartaLaPendiente() throws Exception {
        // Un journal caliente (cierre a mitad de una escritura) exige deshacer la transacción,
        // que en solo lectura no se puede: no se sabe cuántas cuentas hay y se descarta
        Files.writeString(dataDir.resolve("taskmaster.db-journal"), "journal caliente");
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertOldDatabase(database);
        assertThat(dataDir.resolve("taskmaster.db-journal")).hasContent("journal caliente");
        assertThat(dataDir.resolve("restore-failed-" + STAMP).resolve("taskmaster.db")).hasContent("nueva");
        assertThat(notice).exists();
    }

    @Test
    void bdActualSinTablaUsersAplicaLaPendiente() throws Exception {
        BackupTestSupport.execute(database, "DROP TABLE users");
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(database).hasContent("nueva");
        assertThat(notice).doesNotExist();
    }

    @Test
    void sinBdActualAplicaLaPendiente() throws Exception {
        Files.delete(database);
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(database).hasContent("nueva");
        assertThat(dataDir.resolve("restore-previous").resolve(STAMP).resolve("avatars/a.png")).exists();
        assertThat(notice).doesNotExist();
    }

    @Test
    void conservaSoloLasTresUltimasRestauracionesFallidas() throws Exception {
        for (String old : new String[]{"20200101-000000", "20210101-000000", "20220101-000000", "20230101-000000"}) {
            Files.createDirectories(dataDir.resolve("restore-failed-" + old));
        }
        Files.writeString(dataDir.resolve("restore-failed-notas.txt"), "no es una restauración");
        Files.delete(database);
        createDatabase(database, "carlos", "otra");
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        try (var entries = Files.list(dataDir)) {
            assertThat(entries.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("restore-failed-")))
                    .containsExactlyInAnyOrder("restore-failed-" + STAMP, "restore-failed-20230101-000000",
                            "restore-failed-20220101-000000", "restore-failed-notas.txt");
        }
    }

    @Test
    void falloTrasCrearElDirectorioDeAvataresDevuelveLosOriginales() throws IOException {
        // Copia sin avatares: se crea un directorio de avatares vacío. Después falla el
        // borrado de la marca (es un directorio con contenido) y hay que deshacer
        Files.createDirectories(pending.resolve("LISTA"));
        Files.writeString(pending.resolve("LISTA/bloqueo"), "x");
        Files.writeString(pending.resolve("taskmaster.db"), "nueva");

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertOldDatabase(database);
        assertThat(tree(avatarsDir)).containsExactly("a.png");
        assertThat(avatarsDir.resolve("a.png")).hasContent("avatar viejo");
        assertThat(dataDir.resolve("restore-previous").resolve(STAMP)).doesNotExist();
        assertThat(dataDir.resolve("restore-failed-" + STAMP).resolve("taskmaster.db")).hasContent("nueva");
        assertThat(notice).exists();
    }

    @Test
    void deshacerCompletaUnOriginalBorradoAMedias() throws IOException {
        // Estado tras copiar avatars/ a otro sistema de ficheros y fallar el borrado del
        // original a mitad: el destino está completo y el origen conserva solo una parte
        Path copy = dataDir.resolve("restore-previous").resolve(STAMP).resolve("avatars");
        Files.createDirectories(copy.resolve("sub"));
        Files.writeString(copy.resolve("a.png"), "avatar viejo");
        Files.writeString(copy.resolve("c.png"), "otro avatar");
        Files.writeString(copy.resolve("sub/d.png"), "anidado");
        Files.delete(avatarsDir.resolve("a.png"));
        Files.writeString(avatarsDir.resolve("c.png"), "otro avatar");
        Deque<Path[]> moves = new ArrayDeque<>();
        moves.push(new Path[]{avatarsDir, copy});

        PendingRestoreApplier.undo(moves);

        assertThat(tree(avatarsDir)).containsExactlyInAnyOrder("a.png", "c.png", "sub", "sub/d.png");
        assertThat(avatarsDir.resolve("a.png")).hasContent("avatar viejo");
        assertThat(copy).doesNotExist();
    }

    @Test
    void avataresFueraDelDirectorioDeDatosNoAplica() throws IOException {
        Path outside = root.resolve("otros/avatars");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("a.png"), "avatar viejo");
        preparePending(true);
        Files.writeString(dataDir.resolve("backup-tmp-" + UUID + ".zip"), "zip");
        Set<String> before = tree(root);

        applier.apply(new BackupPaths(database, outside), "127.0.0.1", 0, CLOCK);

        assertThat(tree(root)).isEqualTo(before);
        assertOldDatabase(database);
    }

    @Test
    void unaCuentaEnLaBdActualAplicaLaPendiente() throws Exception {
        Files.delete(database);
        createDatabase(database, "carlos");
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(database).hasContent("nueva");
        assertThat(dataDir.resolve("restore-previous").resolve(STAMP).resolve("taskmaster.db")).exists();
    }

    @Test
    void borraTemporalesHuerfanos() throws IOException {
        Files.createDirectories(dataDir.resolve("backup-tmp-" + UUID + "/sub"));
        Files.writeString(dataDir.resolve("backup-tmp-" + UUID + "/sub/taskmaster.db"), "x");
        Files.writeString(dataDir.resolve("backup-tmp-" + UUID + ".zip"), "zip");
        Files.writeString(dataDir.resolve("restore-upload-" + UUID + ".zip"), "zip");
        Files.createDirectories(dataDir.resolve("restore-staging-" + UUID));
        Files.writeString(dataDir.resolve("restore-staging-" + UUID + "/taskmaster.db"), "x");
        Files.writeString(dataDir.resolve("config.properties"), "language=es");

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(tree(dataDir)).containsExactlyInAnyOrder(
                "taskmaster.db", "avatars", "avatars/a.png", "config.properties");
    }

    @Test
    void noBorraFicherosAjenosConElMismoPrefijo() throws IOException {
        Files.writeString(dataDir.resolve("backup-tmp-notas.txt"), "mías");
        Files.writeString(dataDir.resolve("restore-upload-" + UUID + ".txt"), "mías");
        Files.createDirectories(dataDir.resolve("restore-staging-fotos"));

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(tree(dataDir)).containsExactlyInAnyOrder("taskmaster.db", "avatars", "avatars/a.png",
                "backup-tmp-notas.txt", "restore-upload-" + UUID + ".txt", "restore-staging-fotos");
    }

    /** Comprueba que el fichero es la base de datos «actual» del {@code setUp}. */
    private void assertOldDatabase(Path file) throws IOException {
        assertThat(Files.readAllBytes(file)).isEqualTo(oldDatabase);
    }

    /** Deja en {@code restore-pending/} una copia con BD «nueva» y el avatar {@code b.png}. */
    private void preparePending(boolean ready) throws IOException {
        Files.createDirectories(pending.resolve("avatars"));
        Files.writeString(pending.resolve("taskmaster.db"), "nueva");
        Files.writeString(pending.resolve("manifest.properties"), "formato=1\n");
        Files.writeString(pending.resolve("avatars/b.png"), "avatar nuevo");
        if (ready) {
            Files.writeString(pending.resolve("LISTA"), "");
        }
    }
}
