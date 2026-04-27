package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.repository.UserSettingsRepository;
import com.taskmaster.taskmasterbackend.service.ProjectService;
import com.taskmaster.taskmasterbackend.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Componente que ejecuta el vaciado automático de la papelera de forma programada.
 *
 * <p>Cada día a medianoche recorre la configuración de todos los usuarios y elimina
 * físicamente las tareas y proyectos en papelera cuya antigüedad supera el periodo
 * de retención configurado por cada usuario (7, 15 o 30 días).</p>
 *
 * @author Carlos
 */
@Component
@RequiredArgsConstructor
public class TrashScheduler {

    private final TaskService taskService;
    private final ProjectService projectService;
    private final UserSettingsRepository userSettingsRepository;

    /**
     * Vacía la papelera de todos los usuarios eliminando los elementos expirados.
     * Se ejecuta automáticamente cada día a medianoche ({@code cron = "0 0 0 * * *"}).
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
