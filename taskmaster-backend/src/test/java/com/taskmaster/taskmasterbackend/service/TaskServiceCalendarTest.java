package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.CalendarTaskDTO;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TaskService#getTasksForCalendar}: rango del mes y mapeo a DTO.
 *
 * @author Carlos
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceCalendarTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private TaskService taskService;

    @Test
    void consultaElMesCompletoYMapeaADto() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end   = LocalDate.of(2026, 2, 28);
        Task task = Task.builder()
                .id(7L)
                .title("Entrega")
                .dueDate(LocalDate.of(2026, 2, 10))
                .status(TaskStatus.IN_PROGRESS)
                .priority(TaskPriority.HIGH)
                .build();
        when(taskRepository.findActiveRootTasksForCalendar(1L, start, end)).thenReturn(List.of(task));

        List<CalendarTaskDTO> result = taskService.getTasksForCalendar(1L, 2026, 2);

        verify(taskRepository).findActiveRootTasksForCalendar(1L, start, end);
        assertThat(result).containsExactly(new CalendarTaskDTO(
                7L, "Entrega", LocalDate.of(2026, 2, 10), TaskStatus.IN_PROGRESS, TaskPriority.HIGH));
    }
}
