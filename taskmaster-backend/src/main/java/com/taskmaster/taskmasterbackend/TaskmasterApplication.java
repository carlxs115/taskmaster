package com.taskmaster.taskmasterbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskmasterApplication {
	public static void main(String[] args) {
		SpringApplication.run(TaskmasterApplication.class, args);
	}
}
