package com.taskmaster.taskmasterbackend;

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

	/**
	 * Inicia la aplicación Spring Boot.
	 *
	 * <p>Antes de arrancar el contexto de Spring, garantiza que el directorio
	 * de datos {@code ~/.taskmaster} existe, necesario para que SQLite pueda
	 * crear el fichero de base de datos.</p>
	 *
	 * @param args argumentos de línea de comandos
	 */
	public static void main(String[] args) {
		Path dataDir = Paths.get(System.getProperty("user.home"), ".taskmaster");
		try {
			Files.createDirectories(dataDir);
		} catch (IOException e) {
			System.err.println("[TaskMaster] No se pudo crear el directorio de datos: " + e.getMessage());
		}
		SpringApplication.run(TaskmasterApplication.class, args);
	}
}
