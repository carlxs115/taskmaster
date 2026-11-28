package com.taskmaster.taskmasterbackend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vigila que ningún fichero .properties del repositorio contenga el carácter de
 * sustitución U+FFFD, señal de que un guardado con otra codificación hizo perder
 * los caracteres acentuados (BUG-9). No usa Spring ni base de datos: lee los
 * ficheros de ambos módulos por ruta relativa a la raíz del repo.
 */
class PropertiesEncodingTest {

    private static final char REPLACEMENT_CHAR = '\uFFFD';

    @Test
    void seEncuentranLosBundlesDeMensajes() throws IOException {
        List<String> nombres = propertiesDelRepo().stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());

        assertTrue(nombres.contains("messages_es.properties"), "No se ha encontrado messages_es.properties");
        assertTrue(nombres.contains("messages_en.properties"), "No se ha encontrado messages_en.properties");
    }

    @Test
    void ningunPropertiesContieneElCaracterDeSustitucion() throws IOException {
        Path raiz = raizDelRepo();
        List<String> fallos = new ArrayList<>();

        for (Path fichero : propertiesDelRepo()) {
            String contenido = new String(Files.readAllBytes(fichero), StandardCharsets.UTF_8);
            String[] lineas = contenido.split("\r?\n", -1);
            for (int i = 0; i < lineas.length; i++) {
                if (lineas[i].indexOf(REPLACEMENT_CHAR) >= 0) {
                    fallos.add(raiz.relativize(fichero) + ":" + (i + 1));
                }
            }
        }

        assertEquals(List.of(), fallos, "Ficheros .properties con U+FFFD (fichero:línea)");
    }

    private static List<Path> propertiesDelRepo() throws IOException {
        Path raiz = raizDelRepo();
        List<Path> resultado = new ArrayList<>();
        for (String modulo : List.of("taskmaster-frontend", "taskmaster-backend")) {
            Path src = raiz.resolve(modulo).resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(src)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".properties"))
                        .forEach(resultado::add);
            }
        }
        return resultado;
    }

    /** Sube desde el directorio de trabajo hasta el que contiene taskmaster-frontend. */
    private static Path raizDelRepo() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null) {
            if (Files.isDirectory(actual.resolve("taskmaster-frontend"))) {
                return actual;
            }
            actual = actual.getParent();
        }
        throw new IllegalStateException("No se encuentra la raíz del repositorio (taskmaster-frontend)");
    }
}
