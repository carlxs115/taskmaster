package com.taskmaster.taskmasterbackend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utilidades compartidas por los tests de la copia de seguridad (FUN-1): crean bases de
 * datos SQLite y zips en directorios temporales de JUnit ({@code @TempDir}).
 *
 * @author Carlos
 */
public final class BackupTestSupport {

    private BackupTestSupport() {
    }

    /**
     * Crea una base de datos SQLite con las tablas mínimas de TaskMaster
     * ({@code users}, {@code projects} y {@code tasks}) y los usuarios indicados.
     *
     * @param database  fichero que se crea
     * @param usernames usuarios que se insertan
     */
    public static void createDatabase(Path database, String... usernames) throws SQLException, IOException {
        Files.createDirectories(database.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT NOT NULL, password TEXT NOT NULL)");
            statement.execute("CREATE TABLE projects (id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("CREATE TABLE tasks (id INTEGER PRIMARY KEY, title TEXT)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users (username, password) VALUES (?, 'hash')")) {
                for (String username : usernames) {
                    insert.setString(1, username);
                    insert.executeUpdate();
                }
            }
        }
    }

    /**
     * Ejecuta sentencias SQL sobre una base de datos de test.
     *
     * @param database fichero de la base de datos
     * @param sql      sentencias que se ejecutan en orden
     */
    public static void execute(Path database, String... sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            for (String sentence : sql) {
                statement.execute(sentence);
            }
        }
    }

    /**
     * Crea un zip en memoria con las entradas indicadas, en orden. Los nombres que
     * terminan en {@code /} se escriben como directorios.
     *
     * @param entries nombre de cada entrada y su contenido
     * @return bytes del zip
     */
    public static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Lista, relativo a {@code root}, todo lo que hay debajo (ficheros y directorios).
     *
     * @param root directorio raíz
     * @return rutas relativas con {@code /} como separador, ordenadas
     */
    public static TreeSet<String> tree(Path root) throws IOException {
        TreeSet<String> result = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> !p.equals(root))
                    .forEach(p -> result.add(root.relativize(p).toString().replace('\\', '/')));
        }
        return result;
    }
}
