package com.taskmaster.taskmasterbackend;

import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TaskDependencyCleanup}: limpieza al arrancar de las dependencias inválidas (SEG-8)
 * y de los registros de trabajo huérfanos (SEG-9).
 *
 * @author Carlos
 */
@ExtendWith(MockitoExtension.class)
class TaskDependencyCleanupTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskDependencyCleanup taskDependencyCleanup;

    @Test
    void cleanOnStartupLimpiaLasDependenciasInvalidas() {
        when(taskRepository.deleteInvalidDependencies()).thenReturn(2);

        taskDependencyCleanup.cleanOnStartup();

        verify(taskRepository).deleteInvalidDependencies();
    }

    @Test
    void cleanOnStartupNoImpideElArranqueSiFalla() {
        when(taskRepository.deleteInvalidDependencies()).thenThrow(new RuntimeException("BD no disponible"));

        assertThatCode(() -> taskDependencyCleanup.cleanOnStartup()).doesNotThrowAnyException();
    }

    @Test
    void cleanOnStartupLimpiaLosRegistrosHuerfanos() {
        when(taskRepository.deleteOrphanWorkLogs()).thenReturn(3);

        taskDependencyCleanup.cleanOnStartup();

        verify(taskRepository).deleteOrphanWorkLogs();
    }

    @Test
    void cleanOnStartupLimpiaLosRegistrosAunqueFallenLasDependencias() {
        when(taskRepository.deleteInvalidDependencies()).thenThrow(new RuntimeException("BD no disponible"));

        assertThatCode(() -> taskDependencyCleanup.cleanOnStartup()).doesNotThrowAnyException();

        verify(taskRepository).deleteOrphanWorkLogs();
    }

    @Test
    void cleanOnStartupNoImpideElArranqueSiFallaLaLimpiezaDeRegistros() {
        when(taskRepository.deleteOrphanWorkLogs()).thenThrow(new RuntimeException("BD no disponible"));

        assertThatCode(() -> taskDependencyCleanup.cleanOnStartup()).doesNotThrowAnyException();

        verify(taskRepository).deleteInvalidDependencies();
    }

    @Test
    void cleanOnStartupSeEjecutaAlArrancar() throws NoSuchMethodException {
        EventListener listener = TaskDependencyCleanup.class.getMethod("cleanOnStartup")
                .getAnnotation(EventListener.class);

        assertThat(listener).isNotNull();
        assertThat(List.of(listener.value())).contains(ApplicationReadyEvent.class);
    }
}
