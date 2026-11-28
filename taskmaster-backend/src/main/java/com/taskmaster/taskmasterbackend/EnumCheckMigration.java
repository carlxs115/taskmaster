package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.model.enums.ThemeType;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pone al día, al arrancar, las restricciones {@code CHECK} de las columnas de enum (BUG-8).
 *
 * <p>El dialecto de SQLite crea cada columna {@code @Enumerated(EnumType.STRING)} con un
 * {@code CHECK (col in (...))} con los valores del enum en el momento de crear la tabla, y
 * {@code ddl-auto=update} no lo modifica nunca. En una BD creada por una versión anterior,
 * un valor nuevo del enum falla al insertarse ({@code SQLITE_CONSTRAINT_CHECK}). Pasó con
 * {@code activity_log.action_type} al actualizar desde la v1.0.0: no admitía
 * {@code DEPENDENCY_ADDED} ni {@code DEPENDENCY_REMOVED}.</p>
 *
 * <p>Si al {@code CHECK} de una columna registrada le falta algún valor de su enum, la tabla se
 * reconstruye en una sola transacción: tabla nueva con el {@code CHECK} ampliado (unión del
 * enum y de los valores que ya admitía, para que la copia no pierda filas), copia de las filas,
 * borrado de la antigua, renombrado y recreación de sus índices.</p>
 *
 * <p>Se ejecuta en {@code @PostConstruct}: inyectar el {@link EntityManagerFactory} garantiza
 * que Flyway (V1 en una BD nueva o <i>baseline</i> en una anterior, ROB-3) y el
 * {@code ddl-auto=update} ya han terminado, y el servidor web aún no acepta peticiones
 * (arranca cuando todos los singletons existen). Un fallo se registra y no impide el arranque.</p>
 *
 * <p>Si una migración de Flyway añade claves foráneas reales, la reconstrucción tendrá que
 * desactivarlas antes ({@code PRAGMA foreign_keys=OFF}, fuera de la transacción).</p>
 *
 * @author Carlos
 */
@Slf4j
@Component
public class EnumCheckMigration {

    /** Columna de enum persistida como texto, con la restricción {@code CHECK} que la acompaña. */
    record EnumColumn(String table, String column, Class<? extends Enum<?>> enumType) {
    }

    /** Todas las columnas {@code @Enumerated(EnumType.STRING)} del modelo. */
    static final List<EnumColumn> COLUMNS = List.of(
            new EnumColumn("tasks", "status", TaskStatus.class),
            new EnumColumn("tasks", "priority", TaskPriority.class),
            new EnumColumn("tasks", "category", TaskCategory.class),
            new EnumColumn("projects", "status", TaskStatus.class),
            new EnumColumn("projects", "priority", TaskPriority.class),
            new EnumColumn("projects", "category", TaskCategory.class),
            new EnumColumn("work_logs", "activity_type", ActivityType.class),
            new EnumColumn("user_settings", "theme", ThemeType.class),
            new EnumColumn("activity_log", "action_type", ActionType.class)
    );

    /** Sufijo de la tabla temporal durante la reconstrucción. */
    static final String NEW_TABLE_SUFFIX = "__bug8_new";

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z_]+$");
    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

    private final DataSource dataSource;

    /**
     * @param dataSource           origen de datos de la aplicación
     * @param entityManagerFactory solo fuerza el orden: se crea, con el {@code ddl-auto}, antes que este bean
     */
    public EnumCheckMigration(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
    }

    /**
     * Revisa cada tabla con columnas de enum y reconstruye las que tengan un {@code CHECK} desfasado.
     * El fallo de una tabla se registra y no impide revisar las demás ni arrancar.
     */
    @PostConstruct
    public void migrate() {
        Map<String, List<EnumColumn>> byTable = new LinkedHashMap<>();
        for (EnumColumn c : COLUMNS) {
            byTable.computeIfAbsent(c.table(), t -> new ArrayList<>()).add(c);
        }
        for (Map.Entry<String, List<EnumColumn>> entry : byTable.entrySet()) {
            try {
                migrateTable(entry.getKey(), entry.getValue());
            } catch (SQLException | RuntimeException e) {
                // No debe impedir el arranque; la tabla queda como estaba y se reintenta en el próximo
                log.error("Falló la actualización del CHECK de enums de la tabla {}", entry.getKey(), e);
            }
        }
    }

    private void migrateTable(String table, List<EnumColumn> columns) throws SQLException {
        requireIdentifier(table);
        try (Connection connection = dataSource.getConnection()) {
            String createSql = tableSql(connection, table);
            if (createSql == null) {
                return; // La tabla no existe: nada que migrar
            }

            String newSql = createSql;
            List<String> changes = new ArrayList<>();
            for (EnumColumn c : columns) {
                requireIdentifier(c.column());
                Matcher m = checkPattern(c.column()).matcher(newSql);
                if (!m.find()) {
                    continue; // Sin CHECK para esta columna: no hay nada que ampliar
                }
                Set<String> allowed = literals(m.group(1));
                Set<String> merged = new LinkedHashSet<>();
                for (Enum<?> value : c.enumType().getEnumConstants()) {
                    merged.add(value.name());
                }
                if (allowed.containsAll(merged)) {
                    continue;
                }
                List<String> added = new ArrayList<>(merged);
                added.removeAll(allowed);
                merged.addAll(allowed); // Conserva los valores antiguos: la copia nunca falla
                newSql = newSql.substring(0, m.start(1)) + toSqlList(merged) + newSql.substring(m.end(1));
                changes.add(c.column() + " +" + added);
            }
            if (changes.isEmpty()) {
                return;
            }

            rebuild(connection, table, renameTable(newSql, table));
            log.warn("Actualizado el CHECK de enums de la tabla {}: {}", table, changes);
        }
    }

    private void rebuild(Connection connection, String table, String newCreateSql) throws SQLException {
        String newTable = table + NEW_TABLE_SUFFIX;
        boolean autoCommit = connection.getAutoCommit();
        boolean committed = false;
        connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) {
            List<String> indexes = indexSql(connection, table);
            String cols = String.join(", ", columnNames(connection, table));

            st.executeUpdate(newCreateSql);
            st.executeUpdate("INSERT INTO " + newTable + " (" + cols + ") SELECT " + cols + " FROM " + table);
            long before = count(st, table);
            long after = count(st, newTable);
            if (before != after) {
                throw new SQLException("La copia de " + table + " no conserva todas las filas: "
                        + before + " frente a " + after);
            }
            st.executeUpdate("DROP TABLE " + table);
            st.executeUpdate("ALTER TABLE " + newTable + " RENAME TO " + table);
            for (String index : indexes) {
                st.executeUpdate(index);
            }
            connection.commit();
            committed = true;
        } finally {
            // Ante cualquier fallo (también un Error) se deshace antes de restaurar el autocommit,
            // que con sqlite-jdbc confirmaría una reconstrucción a medias
            if (!committed) {
                connection.rollback();
            }
            connection.setAutoCommit(autoCommit);
        }
    }

    private static String tableSql(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static List<String> indexSql(Connection connection, String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }

    private static List<String> columnNames(Connection connection, String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                result.add("\"" + rs.getString("name").replace("\"", "\"\"") + "\"");
            }
        }
        return result;
    }

    private static long count(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** {@code check (col in ( ... ))}; el grupo 1 es la lista de literales. */
    static Pattern checkPattern(String column) {
        return Pattern.compile("check\\s*\\(\\s*\"?" + Pattern.quote(column) + "\"?\\s+in\\s*\\(([^)]*)\\)\\s*\\)",
                Pattern.CASE_INSENSITIVE);
    }

    private static String renameTable(String createSql, String table) {
        Matcher m = Pattern.compile("^\\s*create\\s+table\\s+[\"`\\[]?" + Pattern.quote(table) + "[\"`\\]]?",
                Pattern.CASE_INSENSITIVE).matcher(createSql);
        if (!m.find()) {
            throw new IllegalStateException("CREATE TABLE inesperado para " + table);
        }
        return "create table " + table + NEW_TABLE_SUFFIX + createSql.substring(m.end());
    }

    private static Set<String> literals(String list) {
        Set<String> result = new LinkedHashSet<>();
        Matcher m = LITERAL.matcher(list);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    private static String toSqlList(Set<String> values) {
        return String.join(",", values.stream().map(v -> "'" + v.replace("'", "''") + "'").toList());
    }

    private static void requireIdentifier(String name) {
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Identificador no válido: " + name);
        }
    }
}
