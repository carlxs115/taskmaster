package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.repository.UserSettingsRepository;
import com.taskmaster.taskmasterbackend.service.ProjectService;
import com.taskmaster.taskmasterbackend.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Componente que ejecuta el vaciado automático de la papelera de forma programada.
 *
 * <p>Cada día a medianoche recorre la configuración de todos los usuarios y elimina
 * físicamente las tareas y proyectos en papelera cuya antigüedad supera el periodo
 * de retención configurado por cada usuario (7, 15 o 30 días).</p>
 *
 * <p>Cada usuario tiene su propio periodo de retención, por lo que el scheduler
 * procesa cada configuración de forma independiente para no afectar a otros usuarios.</p>
 *
 * @author Carlos
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashScheduler {

    private final TaskService taskService;
    private final ProjectService projectService;
    private final UserSettingsRepository userSettingsRepository;

    /**
     * Vacía la papelera de todos los usuarios eliminando los elementos expirados.
     * Se ejecuta automáticamente cada día a medianoche ({@code cron = "0 0 0 * * *"}).
     *
     * <p>Para cada usuario, aplica su periodo de retención personal al purgar
     * sus tareas y proyectos, evitando que la configuración de un usuario
     * afecte a los datos de otro.</p>
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void purgeExpiredItems() {
        log.info("Iniciando purga automática de papelera");

        // Procesamos cada usuario con su propio periodo de retención
        userSettingsRepository.findAll().forEach(settings -> {
            Long userId = settings.getUser().getId();
            int days    = settings.getTrashRetentionDays();

            log.debug("Purgando papelera del usuario {} con retención de {} días", userId, days);

            // Pasamos el userId para que cada purga solo afecte al usuario correspondiente
            taskService.purgeExpiredTasks(userId, days);
            projectService.purgeExpiredProjects(userId, days);
        });

        log.info("Purga automática de papelera completada");
    }
}
