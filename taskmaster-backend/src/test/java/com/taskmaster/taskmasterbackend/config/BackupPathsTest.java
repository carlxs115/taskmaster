package com.taskmaster.taskmasterbackend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link BackupPaths}: rutas de la base de datos y de los avatares deducidas de la
 * configuración efectiva (FUN-1). No crea ningún fichero: solo resuelve rutas dentro de
 * {@code @TempDir}.
 *
 * @author Carlos
 */
class BackupPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void resuelveUrlNormalYDirectoriosDeTrabajo() {
        Path db = tempDir.resolve("datos/taskmaster.db");
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:sqlite:" + db)
                .withProperty("taskmaster.storage.avatars-dir", tempDir.resolve("datos/avatars").toString());

        BackupPaths paths = BackupPaths.resolve(env).orElseThrow();

        assertThat(paths.database()).isEqualTo(db);
        assertThat(paths.avatarsDir()).isEqualTo(tempDir.resolve("datos/avatars"));
        assertThat(paths.dataDir()).isEqualTo(tempDir.resolve("datos"));
        assertThat(paths.pendingDir()).isEqualTo(tempDir.resolve("datos/restore-pending"));
        assertThat(paths.readyMarker()).isEqualTo(tempDir.resolve("datos/restore-pending/LISTA"));
        assertThat(paths.previousRoot()).isEqualTo(tempDir.resolve("datos/restore-previous"));
    }

    @Test
    void quitaFileYParametrosYNormaliza() {
        Path db = tempDir.resolve("datos/taskmaster.db");
        String url = "jdbc:sqlite:file:" + tempDir.resolve("datos/sub/../taskmaster.db") + "?journal_mode=DELETE";

        assertThat(BackupPaths.databasePath(url)).contains(db);
    }

    @Test
    void baseDeDatosEnMemoriaNoTieneRutas() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:sqlite::memory:")
                .withProperty("taskmaster.storage.avatars-dir", tempDir.resolve("avatars").toString());

        assertThat(BackupPaths.resolve(env)).isEmpty();
        assertThat(BackupPaths.databasePath("jdbc:sqlite:file::memory:?cache=shared")).isEmpty();
        assertThat(BackupPaths.databasePath("jdbc:sqlite:")).isEmpty();
        assertThat(BackupPaths.databasePath("jdbc:h2:mem:test")).isEmpty();
        assertThat(BackupPaths.databasePath(null)).isEmpty();
    }

    @Test
    void sinDirectorioDeAvataresNoTieneRutas() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:sqlite:" + tempDir.resolve("taskmaster.db"));

        assertThat(BackupPaths.resolve(env)).isEmpty();
    }

    @Test
    void resuelvePlaceholders() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("base", tempDir.toString())
                .withProperty("spring.datasource.url", "jdbc:sqlite:${base}/datos/taskmaster.db")
                .withProperty("taskmaster.storage.avatars-dir", "${base}/datos/avatars");

        BackupPaths paths = BackupPaths.resolve(env).orElseThrow();

        assertThat(paths.database()).isEqualTo(tempDir.resolve("datos/taskmaster.db"));
        assertThat(paths.avatarsDir()).isEqualTo(tempDir.resolve("datos/avatars"));
    }

    @Test
    void avataresDentroOFueraDelDirectorioDeDatos() {
        Path db = tempDir.resolve("datos/taskmaster.db");

        assertThat(new BackupPaths(db, tempDir.resolve("datos/avatars")).avatarsInDataDir()).isTrue();
        assertThat(new BackupPaths(db, tempDir.resolve("datos/sub/avatars")).avatarsInDataDir()).isTrue();
        assertThat(new BackupPaths(db, tempDir.resolve("otros/avatars")).avatarsInDataDir()).isFalse();
        assertThat(new BackupPaths(db, tempDir.resolve("datos2/avatars")).avatarsInDataDir()).isFalse();
        assertThat(new BackupPaths(db, tempDir.resolve("datos")).avatarsInDataDir()).isFalse();
        assertThat(new BackupPaths(db, tempDir).avatarsInDataDir()).isFalse();
    }

    @Test
    void nombresDeTemporales() {
        String uuid = "0f8fad5b-d9cb-469f-a165-70867728950e";

        assertThat(BackupPaths.TEMPORARY_NAME.matcher("backup-tmp-" + uuid).matches()).isTrue();
        assertThat(BackupPaths.TEMPORARY_NAME.matcher("backup-tmp-" + uuid + ".zip").matches()).isTrue();
        assertThat(BackupPaths.TEMPORARY_NAME.matcher("restore-upload-" + uuid + ".zip").matches()).isTrue();
        assertThat(BackupPaths.TEMPORARY_NAME.matcher("restore-staging-" + uuid).matches()).isTrue();
        assertThat(BackupPaths.TEMPORARY_NAME.matcher("backup-tmp-notas.txt").matches()).isFalse();
        assertThat(BackupPaths.TEMPORARY_NAME.matcher("restore-upload-" + uuid).matches()).isFalse();
        assertThat(BackupPaths.TEMPORARY_NAME.matcher("restore-staging-" + uuid + ".zip").matches()).isFalse();
        assertThat(BackupPaths.TEMPORARY_NAME.matcher("backup-tmp-" + uuid.toUpperCase()).matches()).isFalse();
    }

    @Test
    void lasVariablesDeEntornoMandanComoEnElEntornoDePruebas() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:sqlite:" + tempDir.resolve("real/taskmaster.db"))
                .withProperty("taskmaster.storage.avatars-dir", tempDir.resolve("real/avatars").toString());
        // Igual que pruebas.sh: SPRING_DATASOURCE_URL y TASKMASTER_STORAGE_AVATARSDIR
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SPRING_DATASOURCE_URL", "jdbc:sqlite:" + tempDir.resolve("pruebas/taskmaster.db"),
                        "TASKMASTER_STORAGE_AVATARSDIR", tempDir.resolve("pruebas/avatars").toString())));

        Optional<BackupPaths> paths = BackupPaths.resolve(env);

        assertThat(paths).isPresent();
        assertThat(paths.get().database()).isEqualTo(tempDir.resolve("pruebas/taskmaster.db"));
        assertThat(paths.get().avatarsDir()).isEqualTo(tempDir.resolve("pruebas/avatars"));
    }
}
