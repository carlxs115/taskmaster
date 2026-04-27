package com.taskmaster.taskmasterbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

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
	 * @param args argumentos de línea de comandos
	 */
	public static void main(String[] args) {
		SpringApplication.run(TaskmasterApplication.class, args);
	}
}
