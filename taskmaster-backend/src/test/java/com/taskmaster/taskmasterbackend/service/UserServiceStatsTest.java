package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.response.UserStatsResponse;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.ActivityLogRepository;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link UserService#getStats}: conteos de tareas activas y tasa de completado.
 *
 * @author Carlos
 */
@ExtendWith(MockitoExtension.class)
class UserServiceStatsTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private WorkLogRepository workLogRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void usaLosConteosDeTareasActivasYCalculaLaTasa() {
        when(taskRepository.countActiveTasks(USER_ID)).thenReturn(4L);
        when(taskRepository.countActiveTasksByStatus(USER_ID, TaskStatus.DONE)).thenReturn(1L);
        when(taskRepository.countActiveTasksByStatus(USER_ID, TaskStatus.TODO)).thenReturn(2L);
        when(taskRepository.countActiveTasksByStatus(USER_ID, TaskStatus.IN_PROGRESS)).thenReturn(1L);
        when(taskRepository.countActiveTasksByStatus(USER_ID, TaskStatus.CANCELLED)).thenReturn(0L);
        when(projectRepository.countByUserIdAndDeletedFalse(USER_ID)).thenReturn(1L);

        UserStatsResponse stats = userService.getStats(USER_ID);

        assertThat(stats.getTotalTasks()).isEqualTo(4);
        assertThat(stats.getCompletedTasks()).isEqualTo(1);
        assertThat(stats.getPendingTasks()).isEqualTo(2);
        assertThat(stats.getInProgressTasks()).isEqualTo(1);
        assertThat(stats.getCancelledTasks()).isEqualTo(0);
        assertThat(stats.getTotalProjects()).isEqualTo(1);
        assertThat(stats.getCompletionRate()).isEqualTo(25);
        verify(taskRepository).countActiveTasks(USER_ID);
    }

    @Test
    void sinTareasLaTasaEsCero() {
        UserStatsResponse stats = userService.getStats(USER_ID);

        assertThat(stats.getTotalTasks()).isZero();
        assertThat(stats.getCompletionRate()).isZero();
    }
}
