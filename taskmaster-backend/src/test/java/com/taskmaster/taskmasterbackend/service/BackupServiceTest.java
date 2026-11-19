package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.response.RestoreStagedResponse;
import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.taskmaster.taskmasterbackend.BackupTestSupport.createDatabase;
import static com.taskmaster.taskmasterbackend.BackupTestSupport.execute;
import static com.taskmaster.taskmasterbackend.BackupTestSupport.tree;
import static com.taskmaster.taskmasterbackend.BackupTestSupport.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Tests de {@link BackupService} (FUN-1): crear la copia, validar y dejar pendiente la
 * restauración, rechazos de ficheros no válidos y cancelación.
 *
 * <p>Sin contexto de Spring: cada test usa una SQLite real dentro de {@code @TempDir} y
 * comprueba que nada se escribe fuera de él.</p>
 *
 * @author Carlos
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    private static final Long USER_ID = 1L;
    private static final String AVATAR = "0f8fad5b-d9cb-469f-a165-70867728950e.png";

    @TempDir
    Path root;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Path dataDir;
    private Path database;
    private Path avatarsDir;
    private Path fixtures;
    private MockEnvironment environment;
    private DriverManagerDataSource dataSource;
    private BackupService service;

    @BeforeEach
    void setUp() throws Exception {
        dataDir = root.resolve("datos");
        database = dataDir.resolve("taskmaster.db");
        avatarsDir = dataDir.resolve("avatars");
        fixtures = root.resolve("fixtures");
        Files.createDirectories(avatarsDir);
        Files.createDirectories(fixtures);
        createDatabase(database, "carlos");

        environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:sqlite:" + database)
                .withProperty("taskmaster.storage.avatars-dir", avatarsDir.toString());
        dataSource = new DriverManagerDataSource("jdbc:sqlite:" + database);

        User user = User.builder().id(USER_ID).username("carlos").password("hash-bcrypt").build();
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(userRepository.count()).thenReturn(1L);
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        lenient().when(passwordEncoder.matches("buena", "hash-bcrypt")).thenReturn(true);

        service = new BackupService(dataSource, userRepository, passwordEncoder, environment);
    }

    // -------------------------------------------------------------------------
    // Crear copia
    // -------------------------------------------------------------------------

    @Test
    void crearCopiaGeneraZipConBdManifiestoYAvatares() throws Exception {
        Files.writeString(avatarsDir.resolve(AVATAR), "png");
        Files.writeString(avatarsDir.resolve("otro.txt"), "no es un avatar");
        Files.writeString(dataDir.resolve("config.properties"), "language=es");
        Files.writeString(dataDir.resolve("startup.log"), "log");

        Path zip = service.createBackup(USER_ID);

        assertThat(zip.getParent()).isEqualTo(dataDir);
        List<String> names = new ArrayList<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            String manifest = new String(file.getInputStream(file.getEntry("manifest.properties")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertThat(manifest).contains("formato=1").contains("creado=");
            Files.copy(file.getInputStream(file.getEntry("taskmaster.db")), fixtures.resolve("copia.db"));
        }
        assertThat(names).containsExactlyInAnyOrder(
                "manifest.properties", "taskmaster.db", "avatars/", "avatars/" + AVATAR);
        assertThat(usernames(fixtures.resolve("copia.db"))).containsExactly("carlos");
        // Solo queda el zip que se devuelve: el directorio temporal se ha borrado
        assertThat(tree(dataDir)).containsExactlyInAnyOrder("taskmaster.db", "avatars", "avatars/" + AVATAR,
                "avatars/otro.txt", "config.properties", "startup.log", zip.getFileName().toString());
    }

    @Test
    void crearCopiaRechazaConVariasCuentas() throws Exception {
        lenient().when(userRepository.count()).thenReturn(2L);

        assertThatThrownBy(() -> service.createBackup(USER_ID)).isInstanceOf(BusinessException.class);
        assertThat(tree(dataDir)).containsExactlyInAnyOrder("taskmaster.db", "avatars");
        assertThat(service.getStatus().isAvailable()).isFalse();
    }

    @Test
    void crearCopiaRechazaSiLaInstantaneaTieneVariasCuentas() throws Exception {
        // El repositorio aún dice una cuenta, pero otra se registró antes del VACUUM INTO
        execute(database, "INSERT INTO users (username, password) VALUES ('otra', 'hash')");

        assertThatThrownBy(() -> service.createBackup(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("única cuenta");
        assertThat(tree(dataDir)).containsExactlyInAnyOrder("taskmaster.db", "avatars");
    }

    @Test
    void avataresFueraDelDirectorioDeDatosNoEstaDisponible() throws Exception {
        Path outside = root.resolve("otros/avatars");
        Files.createDirectories(outside);
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:sqlite:" + database)
                .withProperty("taskmaster.storage.avatars-dir", outside.toString());
        service = new BackupService(dataSource, userRepository, passwordEncoder, env);

        assertThat(service.getStatus().isAvailable()).isFalse();
        assertThatThrownBy(() -> service.createBackup(USER_ID)).isInstanceOf(BusinessException.class);
        assertThat(tree(dataDir)).containsExactlyInAnyOrder("taskmaster.db", "avatars");
    }

    // -------------------------------------------------------------------------
    // Preparar restauración
    // -------------------------------------------------------------------------

    @Test
    void prepararRestauracionDejaPendienteUnaCopiaValida() throws Exception {
        Files.writeString(avatarsDir.resolve(AVATAR), "png");
        byte[] copia = createBackupBytes();
        execute(database, "INSERT INTO users (username, password) VALUES ('despues', 'hash')");
        String hashAntes = sha256(database);

        RestoreStagedResponse response = service.stageRestore(USER_ID, "buena", new ByteArrayInputStream(copia));

        assertThat(response.getUsernames()).containsExactly("carlos");
        assertThat(sha256(database)).isEqualTo(hashAntes);
        assertThat(tree(dataDir.resolve("restore-pending"))).containsExactlyInAnyOrder(
                "LISTA", "manifest.properties", "taskmaster.db", "avatars", "avatars/" + AVATAR);
        assertThat(service.getStatus().isRestorePending()).isTrue();
        assertThat(service.getStatus().isAvailable()).isTrue();
        assertNoTemporaries();
    }

    @Test
    void prepararRestauracionSustituyeUnaPendienteAnterior() throws Exception {
        byte[] copia = createBackupBytes();
        service.stageRestore(USER_ID, "buena", new ByteArrayInputStream(copia));
        Files.writeString(dataDir.resolve("restore-pending/sobra.txt"), "x");

        service.stageRestore(USER_ID, "buena", new ByteArrayInputStream(copia));

        assertThat(dataDir.resolve("restore-pending/sobra.txt")).doesNotExist();
        assertThat(dataDir.resolve("restore-pending/LISTA")).exists();
        assertNoTemporaries();
    }

    @Test
    void prepararRestauracionRechazaContrasenaIncorrecta() throws Exception {
        byte[] copia = createBackupBytes();

        assertRejected(copia, "mala", BusinessException.class);
    }

    @Test
    void prepararRestauracionRechazaConVariasCuentas() throws Exception {
        byte[] copia = createBackupBytes();
        lenient().when(userRepository.count()).thenReturn(2L);

        assertRejected(copia, "buena", BusinessException.class);
    }

    @Test
    void contrasenaIncorrectaDescartaElCuerpoAntesDeRechazar() throws Exception {
        ByteArrayInputStream body = new ByteArrayInputStream(createBackupBytes());

        assertThatThrownBy(() -> service.stageRestore(USER_ID, "mala", body)).isInstanceOf(BusinessException.class);
        assertThat(body.available()).as("el cuerpo se ha leído entero").isZero();
    }

    @Test
    void variasCuentasDescartaElCuerpoAntesDeRechazar() throws Exception {
        ByteArrayInputStream body = new ByteArrayInputStream(createBackupBytes());
        lenient().when(userRepository.count()).thenReturn(2L);

        assertThatThrownBy(() -> service.stageRestore(USER_ID, "buena", body)).isInstanceOf(BusinessException.class);
        assertThat(body.available()).as("el cuerpo se ha leído entero").isZero();
    }

    @Test
    void descartarElCuerpoDejaDeLeerAlPasarDelLimite() {
        service = new BackupService(dataSource, userRepository, passwordEncoder, environment,
                100, BackupService.MAX_DB_BYTES, BackupService.MAX_AVATAR_BYTES, BackupService.MAX_TOTAL_BYTES);
        ByteArrayInputStream body = new ByteArrayInputStream(new byte[1_000_000]);

        assertThatThrownBy(() -> service.stageRestore(USER_ID, "mala", body)).isInstanceOf(BusinessException.class);
        assertThat(body.available()).as("no se lee más allá del límite").isGreaterThan(900_000);
    }

    @Test
    void rechazaUnFicheroQueNoEsZip() throws Exception {
        assertRejected("no soy un zip".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rechazaZipSinBaseDeDatos() throws Exception {
        assertRejected(zip(entries("manifest.properties", manifest("1"))));
    }

    @Test
    void rechazaBaseDeDatosQueNoEsSqlite() throws Exception {
        assertRejected(zip(entries("manifest.properties", manifest("1"),
                "taskmaster.db", "esto no es SQLite".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rechazaSqliteSinTablaUsers() throws Exception {
        Path db = fixtures.resolve("sin-users.db");
        createDatabase(db, "carlos");
        execute(db, "DROP TABLE users");

        assertRejected(validZip(db));
    }

    @Test
    void rechazaSqliteConUnTrigger() throws Exception {
        Path db = fixtures.resolve("trigger.db");
        createDatabase(db, "carlos");
        execute(db, "CREATE TRIGGER t AFTER INSERT ON tasks BEGIN DELETE FROM users; END");

        assertRejected(validZip(db));
    }

    @Test
    void rechazaSqliteConUnaVista() throws Exception {
        Path db = fixtures.resolve("vista.db");
        createDatabase(db, "carlos");
        execute(db, "CREATE VIEW v AS SELECT * FROM users");

        assertRejected(validZip(db));
    }

    @Test
    void rechazaSqliteConUnaTablaVirtual() throws Exception {
        Path db = fixtures.resolve("virtual.db");
        createDatabase(db, "carlos");
        execute(db, "CREATE VIRTUAL TABLE buscar USING fts5(texto)");

        assertRejected(validZip(db));
    }

    @Test
    void rechazaZipConUnaEntradaTruncada() throws Exception {
        Path db = fixtures.resolve("ok.db");
        createDatabase(db, "carlos");
        // El directorio central declara la mitad del tamaño comprimido real de la BD:
        // al descomprimirla, el flujo se acaba antes de tiempo (EOFException)
        byte[] truncated = halveCompressedSize(validZip(db), "taskmaster.db");

        assertRejected(truncated);
    }

    @Test
    void rechazaSqliteSinUsuarios() throws Exception {
        Path db = fixtures.resolve("vacia.db");
        createDatabase(db);

        assertRejected(validZip(db));
    }

    @Test
    void rechazaEntradaConPuntoPunto() throws Exception {
        Path db = fixtures.resolve("ok.db");
        createDatabase(db, "carlos");

        assertRejected(zip(entries("manifest.properties", manifest("1"), "taskmaster.db", Files.readAllBytes(db),
                "../fuera.txt", "x".getBytes(StandardCharsets.UTF_8))));
        assertThat(dataDir.resolve("fuera.txt")).doesNotExist();
    }

    @Test
    void rechazaAvatarConPuntoPunto() throws Exception {
        Path db = fixtures.resolve("ok.db");
        createDatabase(db, "carlos");

        assertRejected(zip(entries("manifest.properties", manifest("1"), "taskmaster.db", Files.readAllBytes(db),
                "avatars/../../fuera.png", "x".getBytes(StandardCharsets.UTF_8))));
        assertThat(dataDir.resolve("fuera.png")).doesNotExist();
    }

    @Test
    void rechazaRutaAbsoluta() throws Exception {
        Path db = fixtures.resolve("ok.db");
        createDatabase(db, "carlos");

        assertRejected(zip(entries("manifest.properties", manifest("1"), "taskmaster.db", Files.readAllBytes(db),
                root.resolve("absoluta.txt").toString(), "x".getBytes(StandardCharsets.UTF_8))));
        assertThat(root.resolve("absoluta.txt")).doesNotExist();
    }

    @Test
    void rechazaNombreDeAvatarNoPermitido() throws Exception {
        Path db = fixtures.resolve("ok.db");
        createDatabase(db, "carlos");

        assertRejected(zip(entries("manifest.properties", manifest("1"), "taskmaster.db", Files.readAllBytes(db),
                "avatars/foto.exe", "x".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rechazaEntradaDuplicada() throws Exception {
        Path db = fixtures.resolve("ok.db");
        createDatabase(db, "carlos");
        // ZipOutputStream no deja escribir dos entradas con el mismo nombre: se escribe
        // «taskmaster.dX» y se renombra en los bytes (cabecera local y directorio central)
        byte[] bytes = zip(entries("manifest.properties", manifest("1"), "taskmaster.db", Files.readAllBytes(db),
                "taskmaster.dX", "x".getBytes(StandardCharsets.UTF_8)));
        byte[] duplicated = replaceAll(bytes, "taskmaster.dX", "taskmaster.db");

        assertRejected(duplicated);
    }

    @Test
    void rechazaManifiestoDeOtroFormato() throws Exception {
        Path db = fixtures.resolve("ok.db");
        createDatabase(db, "carlos");

        assertRejected(zip(entries("manifest.properties", manifest("2"), "taskmaster.db", Files.readAllBytes(db))));
    }

    @Test
    void rechazaCuerpoMayorQueElLimiteCon413() throws Exception {
        byte[] copia = createBackupBytes();
        service = new BackupService(dataSource, userRepository, passwordEncoder, environment,
                copia.length - 1L, BackupService.MAX_DB_BYTES, BackupService.MAX_AVATAR_BYTES,
                BackupService.MAX_TOTAL_BYTES);

        Throwable error = catchRejected(copia, "buena");

        assertThat(error).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rechazaAvatarDescomprimidoMayorQueElLimite() throws Exception {
        Files.write(avatarsDir.resolve(AVATAR), new byte[1000]);
        byte[] copia = createBackupBytes();
        service = new BackupService(dataSource, userRepository, passwordEncoder, environment,
                BackupService.MAX_UPLOAD_BYTES, BackupService.MAX_DB_BYTES, 100,
                BackupService.MAX_TOTAL_BYTES);

        assertRejected(copia);
    }

    @Test
    void rechazaCopiaDescomprimidaMayorQueElTotal() throws Exception {
        byte[] copia = createBackupBytes();
        service = new BackupService(dataSource, userRepository, passwordEncoder, environment,
                BackupService.MAX_UPLOAD_BYTES, BackupService.MAX_DB_BYTES, BackupService.MAX_AVATAR_BYTES,
                1000);

        assertRejected(copia);
    }

    @Test
    void nombresPermitidos() {
        assertThat(BackupService.isAllowedName("manifest.properties")).isTrue();
        assertThat(BackupService.isAllowedName("taskmaster.db")).isTrue();
        assertThat(BackupService.isAllowedName("avatars/")).isTrue();
        assertThat(BackupService.isAllowedName("avatars/" + AVATAR)).isTrue();
        assertThat(BackupService.isAllowedName("avatars/" + AVATAR.replace(".png", ".jpg"))).isTrue();
        assertThat(BackupService.isAllowedName("avatars\\" + AVATAR)).isFalse();
        assertThat(BackupService.isAllowedName("/taskmaster.db")).isFalse();
        assertThat(BackupService.isAllowedName("C:/taskmaster.db")).isFalse();
        assertThat(BackupService.isAllowedName("avatars/../taskmaster.db")).isFalse();
        assertThat(BackupService.isAllowedName("avatars/" + AVATAR.toUpperCase())).isFalse();
        assertThat(BackupService.isAllowedName("config.properties")).isFalse();
    }

    // -------------------------------------------------------------------------
    // Aviso de restauración fallida
    // -------------------------------------------------------------------------

    @Test
    void estadoAvisaDeUnaRestauracionFallidaHastaQueSeDescarta() throws Exception {
        assertThat(service.getStatus().isLastRestoreFailed()).isFalse();
        Files.writeString(dataDir.resolve("restore-failed.aviso"), "20261119-101530");

        assertThat(service.getStatus().isLastRestoreFailed()).isTrue();

        service.cancelPendingRestore(USER_ID);

        assertThat(service.getStatus().isLastRestoreFailed()).isFalse();
        assertThat(dataDir.resolve("restore-failed.aviso")).doesNotExist();
    }

    @Test
    void prepararOtraRestauracionDescartaElAvisoDeFallo() throws Exception {
        byte[] copia = createBackupBytes();
        Files.writeString(dataDir.resolve("restore-failed.aviso"), "20261119-101530");

        service.stageRestore(USER_ID, "buena", new ByteArrayInputStream(copia));

        assertThat(service.getStatus().isLastRestoreFailed()).isFalse();
        assertThat(service.getStatus().isRestorePending()).isTrue();
    }

    @Test
    void sinDisponibilidadNoAvisaDeUnaRestauracionFallida() throws Exception {
        Files.writeString(dataDir.resolve("restore-failed.aviso"), "20261119-101530");
        lenient().when(userRepository.count()).thenReturn(2L);

        assertThat(service.getStatus().isLastRestoreFailed()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Cancelar
    // -------------------------------------------------------------------------

    @Test
    void cancelarRestauracionBorraLaPendiente() throws Exception {
        service.stageRestore(USER_ID, "buena", new ByteArrayInputStream(createBackupBytes()));
        assertThat(service.getStatus().isRestorePending()).isTrue();

        service.cancelPendingRestore(USER_ID);

        assertThat(dataDir.resolve("restore-pending")).doesNotExist();
        assertThat(service.getStatus().isRestorePending()).isFalse();
    }

    @Test
    void cancelarRestauracionRechazaConVariasCuentas() throws Exception {
        service.stageRestore(USER_ID, "buena", new ByteArrayInputStream(createBackupBytes()));
        lenient().when(userRepository.count()).thenReturn(2L);

        assertThatThrownBy(() -> service.cancelPendingRestore(USER_ID)).isInstanceOf(BusinessException.class);
        assertThat(dataDir.resolve("restore-pending/LISTA")).exists();
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    /** Crea una copia con el servicio, la lee y borra el zip temporal. */
    private byte[] createBackupBytes() throws IOException {
        Path zip = service.createBackup(USER_ID);
        byte[] bytes = Files.readAllBytes(zip);
        Files.delete(zip);
        return bytes;
    }

    /** Zip válido salvo, quizá, la base de datos indicada. */
    private byte[] validZip(Path db) throws IOException {
        return zip(entries("manifest.properties", manifest("1"), "taskmaster.db", Files.readAllBytes(db)));
    }

    private static byte[] manifest(String format) {
        return ("formato=" + format + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> entries(Object... nameAndContent) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < nameAndContent.length; i += 2) {
            entries.put((String) nameAndContent[i], (byte[]) nameAndContent[i + 1]);
        }
        return entries;
    }

    private void assertRejected(byte[] body) throws IOException {
        assertRejected(body, "buena", BusinessException.class);
    }

    private void assertRejected(byte[] body, String password, Class<? extends Throwable> type) throws IOException {
        assertThat(catchRejected(body, password)).isInstanceOf(type);
    }

    /**
     * Intenta preparar la restauración, devuelve el error y comprueba que no ha quedado
     * nada: ni pendiente, ni temporales, ni ficheros fuera del directorio de datos.
     */
    private Throwable catchRejected(byte[] body, String password) throws IOException {
        Set<String> rootBefore = tree(root);
        String hashBefore = sha256(database);

        Throwable error = null;
        try {
            service.stageRestore(USER_ID, password, new ByteArrayInputStream(body));
        } catch (RuntimeException e) {
            error = e;
        }

        assertThat(error).as("la restauración debía rechazarse").isNotNull();
        assertThat(dataDir.resolve("restore-pending")).doesNotExist();
        assertNoTemporaries();
        assertThat(tree(root)).isEqualTo(rootBefore);
        assertThat(sha256(database)).isEqualTo(hashBefore);
        assertThat(service.getStatus().isRestorePending()).isFalse();
        return error;
    }

    private void assertNoTemporaries() throws IOException {
        TreeSet<String> names = tree(dataDir);
        assertThat(names).noneMatch(n -> n.startsWith("restore-staging-")
                || n.startsWith("restore-upload-") || n.startsWith("backup-tmp-"));
    }

    private static List<String> usernames(Path db) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT username FROM users ORDER BY username")) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Divide entre dos el tamaño comprimido que declara el directorio central para la
     * entrada indicada (cabecera {@code PK\1\2}: tamaño comprimido en el byte 20, longitud
     * del nombre en el 28 y nombre desde el 46).
     */
    private static byte[] halveCompressedSize(byte[] zip, String name) {
        byte[] result = zip.clone();
        byte[] expected = name.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i + 46 <= result.length; i++) {
            if (buffer.getInt(i) != 0x02014b50) {
                continue;
            }
            int nameLength = Short.toUnsignedInt(buffer.getShort(i + 28));
            byte[] actual = Arrays.copyOfRange(result, i + 46, i + 46 + nameLength);
            if (Arrays.equals(actual, expected)) {
                int compressed = buffer.getInt(i + 20);
                assertThat(compressed).as("la entrada está comprimida").isGreaterThan(2);
                buffer.putInt(i + 20, compressed / 2);
                return result;
            }
        }
        throw new AssertionError("No se encontró la entrada " + name + " en el directorio central");
    }

    private static byte[] replaceAll(byte[] source, String from, String to) {
        byte[] a = from.getBytes(StandardCharsets.US_ASCII);
        byte[] b = to.getBytes(StandardCharsets.US_ASCII);
        byte[] result = source.clone();
        int replaced = 0;
        for (int i = 0; i <= result.length - a.length; i++) {
            boolean match = true;
            for (int j = 0; j < a.length && match; j++) {
                match = result[i + j] == a[j];
            }
            if (match) {
                System.arraycopy(b, 0, result, i, b.length);
                replaced++;
            }
        }
        assertThat(replaced).as("el nombre aparece en la cabecera local y en el directorio central").isEqualTo(2);
        return result;
    }
}
