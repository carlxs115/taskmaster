package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.config.BackupPaths;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.taskmaster.taskmasterbackend.BackupTestSupport.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Tests de las migraciones de Flyway (ROB-3): una BD nueva se crea con V1, que coincide con
 * el esquema que genera Hibernate, y una BD anterior a Flyway (v1.0.0 a v1.1.1, también
 * restaurada con FUN-1) se marca como V1 (<i>baseline</i>) sin perder datos.
 *
 * <p>Cada test usa su propia SQLite en un {@code @TempDir} y arranca la aplicación real con
 * {@link SpringApplicationBuilder}, sin servidor web: los argumentos de línea de comandos mandan
 * sobre el {@code SPRING_DATASOURCE_URL} de {@code validar.sh}. No pasa por {@code main}, así
 * que {@link PendingRestoreApplier} solo corre donde el test lo llama.</p>
 *
 * @author Carlos
 */
class FlywayMigrationTest {

    /** Checksum de {@code V1__esquema_base.sql}: cambia si alguien edita la migración publicada. */
    private static final int CHECKSUM_V1 = 2001146103;

    private static final String V1 = "db/migration/V1__esquema_base.sql";
    private static final String ESQUEMA_V100 = "esquemas/esquema-v1.0.0.sql";
    private static final String BASELINE = "1|BASELINE|1|Esquema anterior a Flyway (v1.0.0-v1.1.1)";
    private static final String V1_SQL = "1|SQL|1|esquema base";

    /** Reloj fijo para la marca de {@code restore-previous/}. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-28T10:15:30Z"), ZoneOffset.UTC);

    /** Columnas de cada tabla en la v1.0.0, con su orden, para comparar los datos valor a valor. */
    private static final Map<String, String> TABLAS_V100 = new LinkedHashMap<>();
    /** Lo mismo con lo que añadió la v1.1.0 (dependencias y duración estimada). */
    private static final Map<String, String> TABLAS_V11 = new LinkedHashMap<>();

    static {
        TABLAS_V100.put("users", "id, avatar_path, birth_date, created_at, email, password, username");
        TABLAS_V100.put("user_settings", "id, theme, trash_retention_days, user_id");
        TABLAS_V100.put("projects",
                "id, category, created_at, deleted, deleted_at, description, name, priority, status, user_id");
        TABLAS_V100.put("tasks", "id, category, created_at, deleted, deleted_at, description, due_date, "
                + "priority, status, title, parent_task_id, project_id, user_id");
        TABLAS_V100.put("work_logs", "id, activity_type, date, hours, note, task_id");
        TABLAS_V100.put("activity_log",
                "id, action_type, created_at, entity_id, entity_name, entity_type, new_value, old_value, user_id");

        TABLAS_V11.putAll(TABLAS_V100);
        TABLAS_V11.put("projects", TABLAS_V100.get("projects") + ", estimated_duration");
        TABLAS_V11.put("tasks", TABLAS_V100.get("tasks") + ", estimated_duration");
        TABLAS_V11.put("task_dependencies", "task_id, depends_on_task_id");
    }

    /** Esquema que genera Hibernate solo, sin Flyway (como antes de ROB-3); se calcula una vez. */
    private static SchemaSnapshot esquemaHibernate;

    @TempDir
    static Path compartido;

    @TempDir
    Path dir;

    @Test
    void lasMigracionesCreanElMismoEsquemaQueHibernate() throws Exception {
        Path conFlyway = esquemaDeFlyway("b");

        // Si alguien añade un campo a una entidad sin escribir su migración, esto falla
        assertThat(SchemaSnapshot.of(conFlyway)).isEqualTo(esquemaHibernate());
        assertThat(esquemaHibernate().tablas()).isNotEmpty()
                .allSatisfy((tabla, t) -> assertThat(t.clavesForaneas()).as(tabla).isEmpty());
    }

    @Test
    void bdNuevaMigraAV1YHibernateNoLaCambia() throws Exception {
        Path db = bd("nueva");

        arrancarYParar(db);

        assertThat(historial(db)).containsExactly(V1_SQL);
        // ddl-auto=update no ha añadido ni alterado nada sobre lo que crea V1
        assertThat(SchemaSnapshot.textoTablas(db)).isEqualTo(SchemaSnapshot.textoTablas(esquemaDeFlyway("b")));
    }

    @Test
    void ficheroVacioExistenteMigraAV1() throws Exception {
        Path db = bd("vacio");
        Files.createFile(db);

        arrancarYParar(db);

        assertThat(historial(db)).containsExactly(V1_SQL);
    }

    @Test
    void bdDeLaV100SinHistorialHaceBaselineYConservaLosDatos() throws Exception {
        Path db = bd("v100");
        ejecutarScript(db, ESQUEMA_V100);
        insertarFilasV100(db);
        Map<String, List<List<Object>>> antes = datos(db, TABLAS_V100);

        arrancarYParar(db);

        assertThat(historial(db)).containsExactly(BASELINE);
        assertThat(datos(db, TABLAS_V100)).isEqualTo(antes);
        // Lo que faltaba de la v1.1.0 lo añade ddl-auto=update, vacío en las filas antiguas
        assertThat(columna(db, "SELECT estimated_duration FROM projects")).containsOnlyNulls().hasSize(2);
        assertThat(columna(db, "SELECT estimated_duration FROM tasks")).containsOnlyNulls().hasSize(3);
        assertThat(columna(db, "SELECT count(*) FROM task_dependencies")).containsExactly(0);
        // El CHECK de activity_log ya admite los valores nuevos (EnumCheckMigration)
        assertThatCode(() -> execute(db, "INSERT INTO activity_log (id, action_type, created_at, user_id) "
                + "VALUES (9, 'DEPENDENCY_ADDED', 0, 1)")).doesNotThrowAnyException();
        execute(db, "DELETE FROM activity_log WHERE id = 9");
        assertThat(SchemaSnapshot.of(db)).isEqualTo(esquemaHibernate());

        // Un segundo arranque no repite el baseline ni toca los datos
        arrancarYParar(db);

        assertThat(historial(db)).containsExactly(BASELINE);
        assertThat(datos(db, TABLAS_V100)).isEqualTo(antes);
    }

    @Test
    void bdDeLaV11SinHistorialHaceBaselineSinTocarElEsquema() throws Exception {
        Path db = bd("v11");
        ejecutarScript(db, V1);
        insertarFilasV11(db);
        Map<String, String> textoAntes = SchemaSnapshot.textoTablas(db);
        Map<String, List<List<Object>>> antes = datos(db, TABLAS_V11);

        arrancarYParar(db);

        assertThat(historial(db)).containsExactly(BASELINE);
        assertThat(SchemaSnapshot.textoTablas(db)).isEqualTo(textoAntes);
        assertThat(datos(db, TABLAS_V11)).isEqualTo(antes);
    }

    @Test
    void bdDeLaV100YaActualizadaHaceBaseline() throws Exception {
        // Lo que hizo la v1.1.x con una BD de la v1.0.0: ddl-auto=update y EnumCheckMigration
        Path db = bd("v100-actualizada");
        ejecutarScript(db, ESQUEMA_V100);
        insertarFilasV100(db);
        Map<String, List<List<Object>>> antes = datos(db, TABLAS_V100);
        arrancarYParar(db, "--spring.flyway.enabled=false");
        assertThat(SchemaSnapshot.textoTablas(db)).isNotEqualTo(SchemaSnapshot.textoTablas(esquemaDeFlyway("b")));

        arrancarYParar(db);

        assertThat(historial(db)).containsExactly(BASELINE);
        assertThat(datos(db, TABLAS_V100)).isEqualTo(antes);
        // El texto de las tablas no coincide con V1 (columna al final, CHECK reconstruido), la estructura sí
        assertThat(SchemaSnapshot.of(db)).isEqualTo(esquemaHibernate());
    }

    @Test
    void copiaRestauradaDeLaV100HaceBaselineTrasAplicarse() throws Exception {
        // Copia anterior a ROB-3 sobre una BD actual con historial V1
        Path db = bdActualConCopiaPendiente("sin-historial", copia -> {
            ejecutarScript(copia, ESQUEMA_V100);
            insertarFilasV100(copia);
        });
        Map<String, List<List<Object>>> copia = datos(pendiente(db), TABLAS_V100);

        new PendingRestoreApplier().apply(new BackupPaths(db, avatares(db)), "127.0.0.1", 0, CLOCK);

        assertThat(pendiente(db)).doesNotExist();
        assertThat(historial(db.resolveSibling("restore-previous").resolve("20261128-101530")
                .resolve("taskmaster.db"))).containsExactly(V1_SQL);
        arrancarYParar(db);
        assertThat(historial(db)).containsExactly(BASELINE);
        assertThat(datos(db, TABLAS_V100)).isEqualTo(copia);
        assertThat(SchemaSnapshot.of(db)).isEqualTo(esquemaHibernate());

        // Una copia hecha después de ROB-3 lleva su historial: no se repite el baseline
        Path db2 = bdActualConCopiaPendiente("con-historial", c -> {
            migrarSoloFlyway(c);
            insertarFilasV11(c);
        });
        Map<String, List<List<Object>>> copia2 = datos(pendiente(db2), TABLAS_V11);

        new PendingRestoreApplier().apply(new BackupPaths(db2, avatares(db2)), "127.0.0.1", 0, CLOCK);
        arrancarYParar(db2);

        assertThat(historial(db2)).containsExactly(V1_SQL);
        assertThat(datos(db2, TABLAS_V11)).isEqualTo(copia2);
    }

    @Test
    void bdConHistorialDeUnaVersionPosteriorArranca() throws Exception {
        Path db = bd("futura");
        Path futuras = Files.createDirectories(dir.resolve("futuras"));
        Files.writeString(futuras.resolve("V2__prueba.sql"), "create table prueba_v2 (id integer);\n");
        Flyway.configure().dataSource(url(db), null, null)
                .locations("classpath:db/migration", "filesystem:" + futuras)
                .load().migrate();
        assertThat(historial(db)).containsExactly(V1_SQL, "2|SQL|1|prueba");

        // La aplicación solo conoce V1: ignora la V2 aplicada (*:future) y arranca
        arrancarYParar(db);

        assertThat(historial(db)).containsExactly(V1_SQL, "2|SQL|1|prueba");
        assertThat(columna(db, "SELECT count(*) FROM prueba_v2")).containsExactly(0);
    }

    @Test
    void laV1NoCambia() throws Exception {
        MigrationInfo[] migraciones = Flyway.configure().dataSource(url(bd("info")), null, null)
                .load().info().all();

        Integer checksum = Arrays.stream(migraciones)
                .filter(m -> "1".equals(m.getVersion().getVersion()))
                .findFirst().orElseThrow().getChecksum();
        assertThat(checksum).as("No modifiques V1: crea una migración V<n> nueva").isEqualTo(CHECKSUM_V1);
    }

    // ------------------------------------------------------------------------------------------
    // Arranque y BD
    // ------------------------------------------------------------------------------------------

    /** Fichero {@code taskmaster.db} (aún sin crear) en un directorio propio con sus avatares. */
    private Path bd(String nombre) throws IOException {
        Path db = dir.resolve(nombre).resolve("taskmaster.db");
        Files.createDirectories(avatares(db));
        return db;
    }

    private static Path avatares(Path db) {
        return db.resolveSibling("avatars");
    }

    private static Path pendiente(Path db) {
        return db.resolveSibling("restore-pending").resolve("taskmaster.db");
    }

    private static String url(Path db) {
        return "jdbc:sqlite:" + db;
    }

    /** Arranca la aplicación completa, sin servidor web, contra {@code db} y la cierra. */
    private static void arrancarYParar(Path db, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.datasource.url=" + url(db),
                "--taskmaster.storage.avatars-dir=" + avatares(db),
                "--spring.main.web-application-type=none"));
        args.addAll(List.of(extra));
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(TaskmasterApplication.class)
                .run(args.toArray(String[]::new))) {
            // Basta con que el contexto arranque: Flyway, ddl-auto y EnumCheckMigration ya han corrido
        }
    }

    /** Esquema de Hibernate solo, con Flyway desactivado, como antes de ROB-3. */
    private static synchronized SchemaSnapshot esquemaHibernate() throws Exception {
        if (esquemaHibernate == null) {
            Path db = compartido.resolve("hibernate").resolve("taskmaster.db");
            Files.createDirectories(avatares(db));
            arrancarYParar(db, "--spring.flyway.enabled=false");
            esquemaHibernate = SchemaSnapshot.of(db);
        }
        return esquemaHibernate;
    }

    /** BD nueva con solo Flyway y después {@link EnumCheckMigration}, como en producción. */
    private Path esquemaDeFlyway(String nombre) throws Exception {
        Path db = bd(nombre);
        if (!Files.exists(db)) {
            migrarSoloFlyway(db);
            new EnumCheckMigration(new DriverManagerDataSource(url(db)), mock(EntityManagerFactory.class)).migrate();
        }
        return db;
    }

    private static void migrarSoloFlyway(Path db) {
        Flyway.configure().dataSource(url(db), null, null).load().migrate();
    }

    @FunctionalInterface
    private interface Preparacion {
        void preparar(Path copia) throws Exception;
    }

    /**
     * BD actual con historial V1 y una cuenta, más una restauración pendiente lista
     * ({@code restore-pending/} con la BD que deja {@code preparacion} y la marca {@code LISTA}).
     */
    private Path bdActualConCopiaPendiente(String nombre, Preparacion preparacion) throws Exception {
        Path db = bd(nombre);
        migrarSoloFlyway(db);
        execute(db, "INSERT INTO users (id, birth_date, email, password, username) "
                + "VALUES (1, 0, 'actual@example.com', 'hash', 'actual')");
        Path copia = pendiente(db);
        Files.createDirectories(copia.resolveSibling("avatars"));
        preparacion.preparar(copia);
        Files.writeString(copia.resolveSibling("LISTA"), "");
        return db;
    }

    /** Ejecuta un script SQL del classpath: sin las líneas de comentario y sentencia a sentencia. */
    private static void ejecutarScript(Path db, String recurso) throws IOException, SQLException {
        String sql;
        try (InputStream in = FlywayMigrationTest.class.getClassLoader().getResourceAsStream(recurso)) {
            assertThat(in).as(recurso).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String sinComentarios = sql.lines().filter(l -> !l.startsWith("--")).collect(Collectors.joining("\n"));
        execute(db, Arrays.stream(sinComentarios.split(";")).map(String::trim)
                .filter(s -> !s.isEmpty()).toArray(String[]::new));
    }

    // ------------------------------------------------------------------------------------------
    // Datos
    // ------------------------------------------------------------------------------------------

    /**
     * Filas de una BD de la v1.0.0: 1 usuario con sus ajustes, 2 proyectos (el 3 en la papelera),
     * 3 tareas (la 5 es subtarea de la 1 y la 2 está en la papelera), 2 registros de trabajo y
     * 3 entradas de actividad. Fechas en milisegundos, como las guarda Hibernate; la papelera, de
     * ayer, para que la purga al arrancar no la vacíe.
     */
    private static void insertarFilasV100(Path db) throws SQLException {
        long ahora = System.currentTimeMillis();
        long ayer = ahora - 86_400_000L;
        execute(db,
                "INSERT INTO users VALUES (1, NULL, 642895200000, " + ahora
                        + ", 'ana@example.com', '$2a$10$hash', 'ana')",
                "INSERT INTO user_settings VALUES (1, 'NOCHE', 30, 1)",
                "INSERT INTO projects (" + TABLAS_V100.get("projects") + ") VALUES "
                        + "(1, 'TRABAJO', " + ahora + ", 0, NULL, 'Proyecto activo', 'Alfa', 'HIGH', 'IN_PROGRESS', 1), "
                        + "(3, 'PERSONAL', " + ahora + ", 1, " + ayer + ", NULL, 'Beta', 'LOW', 'TODO', 1)",
                "INSERT INTO tasks (" + TABLAS_V100.get("tasks") + ") VALUES "
                        + "(1, 'TRABAJO', " + ahora + ", 0, NULL, 'Con subtarea', 1795910400000, 'MEDIUM', 'TODO', "
                        + "'Tarea 1', NULL, 1, 1), "
                        + "(2, 'ESTUDIOS', " + ahora + ", 1, " + ayer + ", NULL, NULL, 'URGENT', 'DONE', "
                        + "'Tarea 2', NULL, NULL, 1), "
                        + "(5, 'TRABAJO', " + ahora + ", 0, NULL, NULL, NULL, 'LOW', 'IN_PROGRESS', "
                        + "'Subtarea 5', 1, 1, 1)",
                "INSERT INTO work_logs (" + TABLAS_V100.get("work_logs") + ") VALUES "
                        + "(1, 'DEVELOPMENT', 1795910400000, 1.5, 'Primera sesión', 1), "
                        + "(2, 'TEST', 1795910400000, 0.25, NULL, 5)",
                "INSERT INTO activity_log (" + TABLAS_V100.get("activity_log") + ") VALUES "
                        + "(1, 'TASK_CREATED', " + ahora + ", 1, 'Tarea 1', 'TASK', NULL, NULL, 1), "
                        + "(2, 'PROJECT_DELETED', " + ahora + ", 3, 'Beta', 'PROJECT', NULL, NULL, 1), "
                        + "(5, 'LOGIN', " + ahora + ", NULL, NULL, NULL, NULL, NULL, 1)");
    }

    /** Las filas de la v1.0.0 más una dependencia y una duración estimada (v1.1.x). */
    private static void insertarFilasV11(Path db) throws SQLException {
        insertarFilasV100(db);
        execute(db,
                "INSERT INTO task_dependencies VALUES (5, 1)",
                "UPDATE projects SET estimated_duration = 1.50 WHERE id = 1",
                "UPDATE tasks SET estimated_duration = 1.50 WHERE id = 1");
    }

    /** {@code SELECT <columnas> FROM <tabla> ORDER BY <columnas>} de cada tabla, valor a valor. */
    private static Map<String, List<List<Object>>> datos(Path db, Map<String, String> tablas) throws SQLException {
        Map<String, List<List<Object>>> resultado = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url(db));
             Statement st = connection.createStatement()) {
            for (Map.Entry<String, String> tabla : tablas.entrySet()) {
                List<List<Object>> filas = new ArrayList<>();
                try (ResultSet rs = st.executeQuery("SELECT " + tabla.getValue() + " FROM " + tabla.getKey()
                        + " ORDER BY " + tabla.getValue().split(",")[0]
                        + ("task_dependencies".equals(tabla.getKey()) ? ", depends_on_task_id" : ""))) {
                    ResultSetMetaData meta = rs.getMetaData();
                    while (rs.next()) {
                        List<Object> fila = new ArrayList<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            fila.add(rs.getObject(i));
                        }
                        filas.add(fila);
                    }
                }
                resultado.put(tabla.getKey(), filas);
            }
        }
        return resultado;
    }

    private static List<Object> columna(Path db, String sql) throws SQLException {
        List<Object> resultado = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url(db));
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                resultado.add(rs.getObject(1));
            }
        }
        return resultado;
    }

    /** Filas de {@code flyway_schema_history} como {@code version|type|success|description}. */
    private static List<String> historial(Path db) throws SQLException {
        List<String> resultado = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url(db));
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT version, type, success, description "
                     + "FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rs.next()) {
                resultado.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getInt(3) + "|" + rs.getString(4));
            }
        }
        return resultado;
    }
}
