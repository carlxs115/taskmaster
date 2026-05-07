package com.taskmaster.taskmasterbackend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Clase principal de la aplicación TaskMaster.
 *
 * <p>Punto de entrada de Spring Boot. La anotación {@code @EnableScheduling}
 * activa las tareas programadas como el vaciado automático de la papelera
 * definido en {@link TrashScheduler}.</p>
 *
 * @author Carlos
 */
@SpringBootApplication
@EnableScheduling
public class TaskmasterApplication {

	// Logger estático necesario porque main() es un método estático
	// y no puede usar la inyección de dependencias de Spring ni @Slf4j de Lombok
	private static final Logger log = LoggerFactory.getLogger(TaskmasterApplication.class);

	/**
	 * Inicia la aplicación Spring Boot.
	 *
	 * <p>Antes de arrancar el contexto de Spring, garantiza que el directorio
	 * de datos {@code ~/.taskmaster} existe, necesario para que SQLite pueda
	 * crear el fichero de base de datos en esa ruta.</p>
	 *
	 * @param args argumentos de línea de comandos
	 */
	public static void main(String[] args) {
		// Creamos el directorio de datos antes de que Spring arranque,
		// porque SQLite necesita que la carpeta exista para crear el fichero .db
		Path dataDir = Paths.get(System.getProperty("user.home"), ".taskmaster");
		try {
			Files.createDirectories(dataDir);
			log.info("Directorio de datos listo en: {}", dataDir);
		} catch (IOException e) {
			// Logueamos el error pero no detenemos el arranque;
			// si el directorio ya existe o SQLite puede crearlo, la app funcionará igualmente
			log.error("No se pudo crear el directorio de datos: {}", e.getMessage());
		}

		SpringApplication.run(TaskmasterApplication.class, args);
	}
}