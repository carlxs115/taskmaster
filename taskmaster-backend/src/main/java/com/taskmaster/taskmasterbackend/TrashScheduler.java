package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.repository.UserSettingsRepository;
import com.taskmaster.taskmasterbackend.service.ProjectService;
import com.taskmaster.taskmasterbackend.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TRASHSCHEDULER
 *
 * Ejecuta el vaciado automático de la papelera cada día a medianoche.
 * Para cada usuario obtiene su periodo de retención configurado
 * y borra físicamente los elementos que lo superan.
 */
@Component
@RequiredArgsConstructor
public class TrashScheduler {

    private final TaskService taskService;
    private final ProjectService projectService;
    private final UserSettingsRepository userSettingsRepository;

    /**
     * Se ejecuta cada día a medianoche.
     * cron = "0 0 0 * * *" significa: segundo 0, minuto 0, hora 0, cualquier día/mes/año
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void purgeExpiredItems() {
        userSettingsRepository.findAll().forEach(settings -> {
            int days = settings.getTrashRetentionDays();
            taskService.purgeExpiredTasks(days);
            projectService.purgeExpiredProjects(days);
        });
    }
}
