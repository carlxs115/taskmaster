package com.taskmaster.taskmasterbackend;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estructura comparable del esquema de una SQLite de TaskMaster, para los tests de Flyway (ROB-3).
 *
 * <p>No depende del texto ni del orden de las columnas: dos BD con el mismo esquema son
 * iguales con {@code equals} aunque una tenga una columna añadida al final con
 * {@code ALTER TABLE} o una tabla reconstruida por {@link EnumCheckMigration}. Es un
 * {@code record} para que AssertJ muestre la diferencia cuando no coinciden.</p>
 *
 * <p>Deja fuera {@code flyway_schema_history} y las tablas internas {@code sqlite_*}.</p>
 *
 * @param tablas cada tabla con su estructura, por nombre
 * @author Carlos
 */
record SchemaSnapshot(Map<String, Tabla> tablas) {

    /** Tabla de historial de Flyway: no es parte del esquema de la aplicación. */
    static final String FLYWAY_HISTORY = "flyway_schema_history";

    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

    /**
     * Estructura de una tabla.
     *
     * @param columnas       columnas de {@code PRAGMA table_info}, por nombre
     * @param checks         literales del {@code CHECK} de cada columna de enum registrada en
     *                       {@link EnumCheckMigration#COLUMNS}; vacío si la columna no lo tiene
     * @param indices        índices de {@code PRAGMA index_list}/{@code index_info}
     * @param clavesForaneas filas de {@code PRAGMA foreign_key_list}
     */
    record Tabla(Map<String, Columna> columnas, Map<String, Set<String>> checks,
                 Set<Indice> indices, List<String> clavesForaneas) {
    }

    /**
     * Columna tal como la describe {@code PRAGMA table_info}.
     *
     * @param tipo    tipo declarado, en minúsculas
     * @param notNull si es {@code not null}
     * @param defecto valor por defecto ({@code null} si no tiene)
     * @param pk      posición en la clave primaria (0 si no forma parte)
     */
    record Columna(String tipo, boolean notNull, String defecto, int pk) {
    }

    /**
     * Índice de una tabla.
     *
     * @param origen   {@code c} (create index), {@code u} (unique) o {@code pk}
     * @param unico    si es único
     * @param columnas columnas, en el orden del índice
     * @param nombre   nombre, solo si {@code origen = 'c'}: el de los autoíndices depende del
     *                 orden de las columnas de la tabla
     */
    record Indice(String origen, boolean unico, List<String> columnas, String nombre) {
    }

    /**
     * Lee el esquema de una BD.
     *
     * @param db fichero de la base de datos
     * @return su estructura
     */
    static SchemaSnapshot of(Path db) throws SQLException {
        Map<String, Tabla> tablas = new TreeMap<>();
        try (Connection connection = open(db)) {
            for (Map.Entry<String, String> tabla : sqlTablas(connection).entrySet()) {
                String nombre = tabla.getKey();
                tablas.put(nombre, new Tabla(columnas(connection, nombre), checks(nombre, tabla.getValue()),
                        indices(connection, nombre), clavesForaneas(connection, nombre)));
            }
        }
        return new SchemaSnapshot(tablas);
    }

    /**
     * Texto de cada {@code CREATE TABLE} de la BD, normalizado: minúsculas, espacios
     * colapsados y sin espacios junto a paréntesis y comas. Sirve para comprobar que nada
     * ha reescrito una tabla (Hibernate, {@link EnumCheckMigration}).
     *
     * @param db fichero de la base de datos
     * @return {@code nombre → sql} normalizado
     */
    static Map<String, String> textoTablas(Path db) throws SQLException {
        Map<String, String> resultado = new TreeMap<>();
        try (Connection connection = open(db)) {
            sqlTablas(connection).forEach((nombre, sql) -> resultado.put(nombre, normalizar(sql)));
        }
        return resultado;
    }

    static String normalizar(String sql) {
        return sql.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*([(),])\\s*", "$1")
                .trim();
    }

    private static Connection open(Path db) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db);
    }

    private static Map<String, String> sqlTablas(Connection connection) throws SQLException {
        Map<String, String> resultado = new TreeMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, sql FROM sqlite_master WHERE type = 'table' "
                     + "AND name NOT LIKE 'sqlite_%' AND name <> '" + FLYWAY_HISTORY + "'")) {
            while (rs.next()) {
                resultado.put(rs.getString(1), rs.getString(2));
            }
        }
        return resultado;
    }

    private static Map<String, Columna> columnas(Connection connection, String tabla) throws SQLException {
        Map<String, Columna> resultado = new TreeMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(\"" + tabla + "\")")) {
            while (rs.next()) {
                resultado.put(rs.getString("name"), new Columna(rs.getString("type").toLowerCase(),
                        rs.getInt("notnull") != 0, rs.getString("dflt_value"), rs.getInt("pk")));
            }
        }
        return resultado;
    }

    private static Map<String, Set<String>> checks(String tabla, String sql) {
        Map<String, Set<String>> resultado = new TreeMap<>();
        for (EnumCheckMigration.EnumColumn c : EnumCheckMigration.COLUMNS) {
            if (!c.table().equals(tabla)) {
                continue;
            }
            Set<String> literales = new TreeSet<>();
            Matcher m = EnumCheckMigration.checkPattern(c.column()).matcher(sql);
            if (m.find()) {
                Matcher lit = LITERAL.matcher(m.group(1));
                while (lit.find()) {
                    literales.add(lit.group(1));
                }
            }
            resultado.put(c.column(), literales);
        }
        return resultado;
    }

    private static Set<Indice> indices(Connection connection, String tabla) throws SQLException {
        List<String[]> lista = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(\"" + tabla + "\")")) {
            while (rs.next()) {
                lista.add(new String[]{rs.getString("name"), rs.getString("origin"), rs.getString("unique")});
            }
        }
        Set<Indice> resultado = new LinkedHashSet<>();
        for (String[] indice : lista) {
            List<String> columnas = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA index_info(\"" + indice[0] + "\")")) {
                while (rs.next()) {
                    columnas.add(rs.getString("name"));
                }
            }
            String origen = indice[1];
            resultado.add(new Indice(origen, !"0".equals(indice[2]), columnas,
                    "c".equals(origen) ? indice[0] : null));
        }
        return resultado; // Set: la igualdad no depende del orden de PRAGMA index_list
    }

    private static List<String> clavesForaneas(Connection connection, String tabla) throws SQLException {
        List<String> resultado = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_key_list(\"" + tabla + "\")")) {
            while (rs.next()) {
                resultado.add(rs.getString("from") + " -> " + rs.getString("table") + "." + rs.getString("to"));
            }
        }
        return resultado;
    }
}
