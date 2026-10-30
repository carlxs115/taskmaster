package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Test de regresión de la regla «no se puede completar una tarea con subtareas
 * pendientes» en {@link TaskService#changeStatus} y {@link TaskService#updateTask}.
 *
 * <p>Usa el {@link TaskRepository} real contra la SQLite temporal que inyecta
 * {@code validar.sh}; el resto de dependencias del servicio son mocks.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskServiceSubtasksTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    private TaskService taskService;

    private User user;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, mock(ProjectService.class),
                mock(UserRepository.class), mock(ActivityLogService.class));
        user = em.persist(User.builder()
                .username("subtareas")
                .email("subtareas@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build());
    }

    @Test
    void changeStatusCompletaSiLasSubtareasEstanHechasOCanceladas() {
        Task padre = em.persist(task("Padre", null, TaskStatus.TODO, false));
        em.persist(task("Hecha", padre, TaskStatus.DONE, false));
        em.persist(task("Cancelada", padre, TaskStatus.CANCELLED, false));
        em.flush();

        Task result = taskService.changeStatus(padre.getId(), TaskStatus.DONE, user.getId());

        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void changeStatusNoCompletaConUnaSubtareaPendiente() {
        Task padre = em.persist(task("Padre", null, TaskStatus.TODO, false));
        em.persist(task("Pendiente", padre, TaskStatus.TODO, false));
        em.flush();

        assertThatThrownBy(() -> taskService.changeStatus(padre.getId(), TaskStatus.DONE, user.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void changeStatusIgnoraSubtareasEnLaPapelera() {
        Task padre = em.persist(task("Padre", null, TaskStatus.TODO, false));
        em.persist(task("Pendiente borrada", padre, TaskStatus.TODO, true));
        em.flush();

        Task result = taskService.changeStatus(padre.getId(), TaskStatus.DONE, user.getId());

        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void updateTaskCompletaSiLaSubtareaEstaCancelada() {
        Task padre = em.persist(task("Padre", null, TaskStatus.TODO, false));
        em.persist(task("Cancelada", padre, TaskStatus.CANCELLED, false));
        em.flush();

        Task result = update(padre);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void updateTaskNoCompletaConUnaSubtareaPendiente() {
        Task padre = em.persist(task("Padre", null, TaskStatus.TODO, false));
        em.persist(task("Pendiente", padre, TaskStatus.IN_PROGRESS, false));
        em.flush();

        assertThatThrownBy(() -> update(padre))
                .isInstanceOf(BusinessException.class);
    }

    private Task update(Task padre) {
        return taskService.updateTask(padre.getId(), padre.getTitle(), null, TaskStatus.DONE,
                TaskPriority.MEDIUM, null, null, user.getId());
    }

    private Task task(String title, Task parent, TaskStatus status, boolean deleted) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .status(status)
                .user(user)
                .parentTask(parent)
                .deleted(deleted)
                .build();
    }
}
