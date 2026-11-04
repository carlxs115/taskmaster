package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test de regresión de SEG-6: solo el propietario de una tarea puede modificarla,
 * cambiar su estado, borrarla o crear subtareas en ella, también si es personal.
 *
 * <p>Usa el {@link TaskRepository} real contra la SQLite temporal que inyecta
 * {@code validar.sh}; el resto de dependencias del servicio son mocks.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskServiceOwnershipTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    private TaskService taskService;

    private User propietario;
    private User ajeno;
    private Task tarea;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        taskService = new TaskService(taskRepository, mock(ProjectService.class),
                userRepository, mock(ActivityLogService.class));

        propietario = em.persist(user("propietario"));
        ajeno = em.persist(user("ajeno"));
        when(userRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        tarea = em.persist(task("Personal", null));
        em.flush();
    }

    @Test
    void updateTaskDeOtroUsuarioNoSeAplica() {
        assertThatThrownBy(() -> taskService.updateTask(tarea.getId(), "Cambiada", null,
                TaskStatus.DONE, TaskPriority.HIGH, null, null, ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        Task enBd = recargar(tarea);
        assertThat(enBd.getTitle()).isEqualTo("Personal");
        assertThat(enBd.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void changeStatusDeOtroUsuarioNoSeAplica() {
        assertThatThrownBy(() -> taskService.changeStatus(tarea.getId(), TaskStatus.DONE, ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(recargar(tarea).getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void changeStatusDeSubtareaDeOtroUsuarioNoSeAplica() {
        Task subtarea = em.persist(task("Subtarea", tarea));
        em.flush();

        assertThatThrownBy(() -> taskService.changeStatus(subtarea.getId(), TaskStatus.DONE, ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(recargar(subtarea).getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void deleteTaskDeOtroUsuarioNoSeAplica() {
        assertThatThrownBy(() -> taskService.deleteTask(tarea.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(recargar(tarea).isDeleted()).isFalse();
    }

    @Test
    void deletePermanentlyDeOtroUsuarioNoSeAplica() {
        assertThatThrownBy(() -> taskService.deletePermanently(tarea.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        em.flush();
        assertThat(taskRepository.findById(tarea.getId())).isPresent();
    }

    @Test
    void createTaskConPadreDeOtroUsuarioNoSeAplica() {
        assertThatThrownBy(() -> taskService.createTask("Intrusa", null, null, null, null,
                null, tarea.getId(), null, ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        em.flush();
        assertThat(taskRepository.findByParentTaskId(tarea.getId())).isEmpty();
    }

    @Test
    void elPropietarioSiCambiaElEstadoYBorraSuTarea() {
        Task result = taskService.changeStatus(tarea.getId(), TaskStatus.DONE, propietario.getId());
        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);

        taskService.deleteTask(tarea.getId(), propietario.getId());
        assertThat(recargar(tarea).isDeleted()).isTrue();
    }

    private Task recargar(Task task) {
        em.flush();
        em.clear();
        return taskRepository.findById(task.getId()).orElseThrow();
    }

    private User user(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
    }

    private Task task(String title, Task parent) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .priority(TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .user(propietario)
                .parentTask(parent)
                .deleted(false)
                .build();
    }
}
