package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.UserSettings;
import com.taskmaster.taskmasterbackend.repository.UserSettingsRepository;
import com.taskmaster.taskmasterbackend.service.ProjectService;
import com.taskmaster.taskmasterbackend.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TrashScheduler}: purga de la papelera al arrancar y a medianoche.
 *
 * @author Carlos
 */
@ExtendWith(MockitoExtension.class)
class TrashSchedulerTest {

    @Mock
    private TaskService taskService;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private TrashScheduler trashScheduler;

    private static UserSettings settings(Long userId, int retentionDays) {
        return UserSettings.builder()
                .user(User.builder().id(userId).build())
                .trashRetentionDays(retentionDays)
                .build();
    }

    @Test
    void purgaAlArrancarConLaRetencionDeCadaUsuario() {
        when(userSettingsRepository.findAll()).thenReturn(List.of(settings(1L, 7), settings(2L, 30)));

        trashScheduler.purgeOnStartup();

        verify(taskService).purgeExpiredTasks(1L, 7);
        verify(projectService).purgeExpiredProjects(1L, 7);
        verify(taskService).purgeExpiredTasks(2L, 30);
        verify(projectService).purgeExpiredProjects(2L, 30);
    }

    @Test
    void purgeOnStartupEscuchaApplicationReadyEvent() throws NoSuchMethodException {
        EventListener listener = TrashScheduler.class.getMethod("purgeOnStartup").getAnnotation(EventListener.class);

        assertThat(listener).isNotNull();
        assertThat(List.of(listener.value())).contains(ApplicationReadyEvent.class);
    }

    @Test
    void unFalloEnLaPurgaDeArranqueNoSePropaga() {
        when(userSettingsRepository.findAll()).thenThrow(new RuntimeException("BD no disponible"));

        assertThatCode(() -> trashScheduler.purgeOnStartup()).doesNotThrowAnyException();
    }

    @Test
    void laPurgaDeMedianocheSigueProgramada() throws NoSuchMethodException {
        Scheduled scheduled = TrashScheduler.class.getMethod("purgeExpiredItems").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
    }
}
