package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Test de regresión de BUG-8: {@link EnumCheckMigration} amplía el {@code CHECK} de las columnas
 * de enum de una BD creada por una versión anterior (la v1.0.0) sin perder datos.
 *
 * <p>Usa una SQLite propia en un {@code @TempDir}, sin contexto de Spring: el fallo es del
 * esquema de SQLite y no se reproduce con mocks de repositorio.</p>
 *
 * @author Carlos
 */
class EnumCheckMigrationTest {

    /** {@code activity_log} tal como la crea la v1.0.0: sin {@code DEPENDENCY_ADDED} ni {@code DEPENDENCY_REMOVED}. */
    private static final String ACTIVITY_LOG_V100 = """
            create table activity_log (
                created_at timestamp not null,
                entity_id bigint,
                id integer,
                user_id bigint not null,
                action_type varchar(255) not null check (action_type in ('TASK_CREATED','TASK_EDITED','TASK_DELETED','TASK_PERMANENTLY_DELETED','TASK_RESTORED','TASK_STATUS_CHANGED','SUBTASK_CREATED','SUBTASK_EDITED','SUBTASK_DELETED','PROJECT_CREATED','PROJECT_EDITED','PROJECT_DELETED','PROJECT_PERMANENTLY_DELETED','PROJECT_RESTORED','PROJECT_STATUS_CHANGED','PROFILE_UPDATED','PASSWORD_CHANGED','LOGIN','LOGOUT')),
                entity_name varchar(255),
                entity_type varchar(255),
                new_value varchar(255),
                old_value varchar(255),
                primary key (id)
            )""";

    private static final String ACTIVITY_LOG_INDEX =
            "create index idx_activity_log_user_id on activity_log (user_id)";

    @TempDir
    Path dir;

    private Path database;
    private EnumCheckMigration migration;

    @BeforeEach
    void setUp() {
        database = dir.resolve("test.db");
        migration = new EnumCheckMigration(new DriverManagerDataSource("jdbc:sqlite:" + database),
                mock(EntityManagerFactory.class));
    }

    @Test
    void insertarDependencyAddedFallaConElCheckDeLaV100() throws SQLException {
        createActivityLogV100();

        assertThatThrownBy(() -> execute(insertLog(10, "DEPENDENCY_ADDED")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SQLITE_CONSTRAINT_CHECK");
    }

    @Test
    void migraActivityLogDeLaV100YAdmiteLosValoresNuevos() throws SQLException {
        createActivityLogV100();
        execute(insertLog(1, "LOGIN"), insertLog(2, "TASK_CREATED"), insertLog(5, "LOGOUT"));

        migration.migrate();

        execute(insertLog(10, "DEPENDENCY_ADDED"), insertLog(11, "DEPENDENCY_REMOVED"));
        assertThat(query("SELECT id || ':' || action_type FROM activity_log ORDER BY id"))
                .containsExactly("1:LOGIN", "2:TASK_CREATED", "5:LOGOUT",
                        "10:DEPENDENCY_ADDED", "11:DEPENDENCY_REMOVED");
        assertThat(query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'activity_log'"))
                .contains("idx_activity_log_user_id");
        assertThat(query("SELECT name FROM sqlite_master WHERE name LIKE '%" + EnumCheckMigration.NEW_TABLE_SUFFIX + "'"))
                .isEmpty();
        assertThatThrownBy(() -> execute(insertLog(12, "XXX")))
                .hasMessageContaining("SQLITE_CONSTRAINT_CHECK");
    }

    @Test
    void noTocaUnaTablaConElCheckAlDia() throws SQLException {
        String values = Arrays.stream(ActionType.values()).map(v -> "'" + v.name() + "'")
                .collect(Collectors.joining(","));
        execute("create table activity_log (id integer, action_type varchar(255) not null check (action_type in ("
                + values + ")), primary key (id))");
        List<String> before = schema();

        migration.migrate();
        migration.migrate();

        assertThat(schema()).isEqualTo(before);
    }

    @Test
    void esIdempotenteTrasMigrar() throws SQLException {
        createActivityLogV100();
        migration.migrate();
        List<String> after = schema();

        migration.migrate();

        assertThat(schema()).isEqualTo(after);
    }

    @Test
    void conservaColumnasAnadidasPorDdlUpdate() throws SQLException {
        execute("create table tasks (id integer, title varchar(255) not null, "
                        + "status varchar(255) not null check (status in ('TODO','IN_PROGRESS','DONE')), primary key (id))",
                "alter table tasks add column estimated_duration numeric(6,2)",
                "insert into tasks (id, title, status, estimated_duration) values (7, 'T', 'DONE', 1.5)");

        migration.migrate();

        execute("insert into tasks (id, title, status) values (8, 'U', 'CANCELLED')");
        assertThat(query("SELECT id || ':' || status || ':' || estimated_duration FROM tasks WHERE id = 7"))
                .containsExactly("7:DONE:1.5");
    }

    @Test
    void conservaValoresAntiguosQueYaNoEstanEnElEnum() throws SQLException {
        execute("create table work_logs (id integer, activity_type varchar(255) not null "
                        + "check (activity_type in ('ANALYSIS','OBSOLETO')), primary key (id))",
                "insert into work_logs (id, activity_type) values (1, 'OBSOLETO')");

        migration.migrate();

        execute("insert into work_logs (id, activity_type) values (2, 'MANAGEMENT')");
        assertThat(query("SELECT activity_type FROM work_logs ORDER BY id")).containsExactly("OBSOLETO", "MANAGEMENT");
    }

    @Test
    void siFallaNoLanzaYDejaLaTablaComoEstaba() throws SQLException {
        createActivityLogV100();
        execute(insertLog(1, "LOGIN"),
                "create table activity_log" + EnumCheckMigration.NEW_TABLE_SUFFIX + " (x integer)");
        List<String> before = schema();

        assertThatCode(() -> migration.migrate()).doesNotThrowAnyException();

        assertThat(schema()).isEqualTo(before);
        assertThat(query("SELECT action_type FROM activity_log")).containsExactly("LOGIN");
    }

    @Test
    void siFallaTrasBorrarLaTablaDeshaceElBorrado() throws SQLException {
        createActivityLogV100();
        // La vista hace fallar el RENAME, que va después del DROP de la tabla original
        execute(insertLog(1, "LOGIN"), insertLog(2, "LOGOUT"),
                "create view activity_log_view as select * from activity_log");
        List<String> before = schema();

        assertThatCode(() -> migration.migrate()).doesNotThrowAnyException();

        assertThat(schema()).isEqualTo(before);
        assertThat(query("SELECT id || ':' || action_type FROM activity_log ORDER BY id"))
                .containsExactly("1:LOGIN", "2:LOGOUT");
    }

    @Test
    void registraTodosLosCamposEnumerated() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        Set<String> mapped = new HashSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.taskmaster.taskmasterbackend.model")) {
            Class<?> entity = Class.forName(bd.getBeanClassName());
            String table = entity.getAnnotation(Table.class).name();
            for (Field field : entity.getDeclaredFields()) {
                if (field.isAnnotationPresent(Enumerated.class)) {
                    mapped.add(table + "." + snakeCase(field.getName()) + ":" + field.getType().getSimpleName());
                }
            }
        }

        Set<String> registered = EnumCheckMigration.COLUMNS.stream()
                .map(c -> c.table() + "." + c.column() + ":" + c.enumType().getSimpleName())
                .collect(Collectors.toSet());
        assertThat(registered).isEqualTo(mapped);
    }

    @Test
    void seEjecutaAntesDeAbrirElServidor() throws NoSuchMethodException {
        assertThat(EnumCheckMigration.class.getMethod("migrate").isAnnotationPresent(PostConstruct.class)).isTrue();
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private void createActivityLogV100() throws SQLException {
        execute(ACTIVITY_LOG_V100, ACTIVITY_LOG_INDEX);
    }

    private static String insertLog(long id, String actionType) {
        return "insert into activity_log (id, created_at, user_id, action_type) values ("
                + id + ", 0, 1, '" + actionType + "')";
    }

    private void execute(String... sql) throws SQLException {
        BackupTestSupport.execute(database, sql);
    }

    private List<String> query(String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    private List<String> schema() throws SQLException {
        return query("SELECT type || '|' || name || '|' || coalesce(sql, '') FROM sqlite_master ORDER BY type, name");
    }

    private static String snakeCase(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
