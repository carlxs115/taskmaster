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
 * un reloj fijo y el puerto 0 (siempre libre) salvo en el test del puerto ocupado.</p>
 *
 * @author Carlos
 */
class PendingRestoreApplierTest {

    /** Reloj fijo: la marca de {@code restore-previous/} es {@value #STAMP}. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T10:15:30Z"), ZoneOffset.UTC);
    private static final String STAMP = "20261119-101530";

    @TempDir
    Path root;

    private Path dataDir;
    private Path database;
    private Path avatarsDir;
    private Path pending;
    private BackupPaths paths;
    private final PendingRestoreApplier applier = new PendingRestoreApplier();

    @BeforeEach
    void setUp() throws IOException {
        dataDir = root.resolve("datos");
        database = dataDir.resolve("taskmaster.db");
        avatarsDir = dataDir.resolve("avatars");
        pending = dataDir.resolve("restore-pending");
        Files.createDirectories(avatarsDir);
        Files.writeString(database, "vieja");
        Files.writeString(avatarsDir.resolve("a.png"), "avatar viejo");
        paths = new BackupPaths(database, avatarsDir);
    }

    @Test
    void sinPendienteNoTocaNada() throws IOException {
        Files.writeString(dataDir.resolve("config.properties"), "language=es");
        Set<String> before = tree(root);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(tree(root)).isEqualTo(before);
        assertThat(database).hasContent("vieja");
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
        assertThat(database).hasContent("vieja");
    }

    @Test
    void aplicaLaPendienteYGuardaElEstadoAnterior() throws IOException {
        Files.writeString(dataDir.resolve("taskmaster.db-journal"), "journal caliente");
        preparePending(true);

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        Path previous = dataDir.resolve("restore-previous").resolve(STAMP);
        assertThat(database).hasContent("nueva");
        assertThat(dataDir.resolve("taskmaster.db-journal")).doesNotExist();
        assertThat(tree(avatarsDir)).containsExactly("b.png");
        assertThat(previous.resolve("taskmaster.db")).hasContent("vieja");
        assertThat(previous.resolve("taskmaster.db-journal")).hasContent("journal caliente");
        assertThat(previous.resolve("avatars/a.png")).hasContent("avatar viejo");
        assertThat(pending).doesNotExist();
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
        assertThat(database).hasContent("vieja");
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
        assertThat(database).hasContent("vieja");
        assertThat(pending).doesNotExist();
        assertThat(failed.resolve("taskmaster.db")).hasContent("nueva");
        assertThat(failed.resolve("avatars/b.png")).exists();
        assertThat(dataDir.resolve("restore-previous").resolve(STAMP)).doesNotExist();

        // No se reintenta en bucle: el siguiente arranque no hace nada
        applier.apply(blocked, "127.0.0.1", 0, CLOCK);
        assertThat(database).hasContent("vieja");
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
        Files.createDirectories(dataDir.resolve("backup-tmp-1/sub"));
        Files.writeString(dataDir.resolve("backup-tmp-1/sub/taskmaster.db"), "x");
        Files.writeString(dataDir.resolve("backup-tmp-2.zip"), "zip");
        Files.writeString(dataDir.resolve("restore-upload-3.zip"), "zip");
        Files.createDirectories(dataDir.resolve("restore-staging-4"));
        Files.writeString(dataDir.resolve("restore-staging-4/taskmaster.db"), "x");
        Files.writeString(dataDir.resolve("config.properties"), "language=es");

        applier.apply(paths, "127.0.0.1", 0, CLOCK);

        assertThat(tree(dataDir)).containsExactlyInAnyOrder(
                "taskmaster.db", "avatars", "avatars/a.png", "config.properties");
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
