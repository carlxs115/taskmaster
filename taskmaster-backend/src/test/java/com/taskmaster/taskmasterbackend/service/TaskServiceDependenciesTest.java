package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
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
import static org.mockito.Mockito.verify;

/**
 * Tests de las dependencias entre tareas de {@link TaskService} (CAL-1, regla de FUN-5):
 * alta y baja de dependencias, prevención de ciclos y bloqueo al pasar a
 * {@code IN_PROGRESS} por las dos vías (edición y {@code PATCH /status}).
 *
 * <p>Usa el {@link TaskRepository} real contra la SQLite temporal que inyecta
 * {@code validar.sh}; el resto de dependencias del servicio son mocks.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskServiceDependenciesTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    private TaskService taskService;
    private ActivityLogService activityLogService;

    private User propietario;
    private User ajeno;
    private Task a;
    private Task b;
    private Task c;

    @BeforeEach
    void setUp() {
        activityLogService = mock(ActivityLogService.class);
        taskService = new TaskService(taskRepository, mock(ProjectService.class),
                mock(UserRepository.class), activityLogService);

        propietario = em.persist(user("propietario"));
        ajeno = em.persist(user("ajeno"));
        a = em.persist(task("A", propietario, TaskStatus.TODO));
        b = em.persist(task("B", propietario, TaskStatus.TODO));
        c = em.persist(task("C", propietario, TaskStatus.TODO));
        em.flush();
    }

    @Test
    void unaTareaNoPuedeDependerDeSiMisma() {
        assertThatThrownBy(() -> taskService.addDependency(a.getId(), a.getId(), propietario.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sí misma");
    }

    @Test
    void addDependencyGuardaLaRelacionYLaRegistra() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());

        assertThat(dependenciasDe(a)).containsExactly("B");
        verify(activityLogService).log(propietario.getId(), ActionType.DEPENDENCY_ADDED, "TASK",
                a.getId(), "A", "B", "");
    }

    @Test
    void addDependencyRechazaUnCicloDirecto() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());
        em.flush();

        assertThatThrownBy(() -> taskService.addDependency(b.getId(), a.getId(), propietario.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ciclo");
        assertThat(dependenciasDe(b)).isEmpty();
    }

    @Test
    void addDependencyRechazaUnCicloTransitivo() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());
        taskService.addDependency(b.getId(), c.getId(), propietario.getId());
        em.flush();

        assertThatThrownBy(() -> taskService.addDependency(c.getId(), a.getId(), propietario.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ciclo");
        assertThat(dependenciasDe(c)).isEmpty();
    }

    @Test
    void addDependencyConUnaTareaAjenaNoSeAplica() {
        Task deOtro = em.persist(task("De otro", ajeno, TaskStatus.TODO));
        em.flush();

        assertThatThrownBy(() -> taskService.addDependency(a.getId(), deOtro.getId(), propietario.getId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> taskService.addDependency(a.getId(), b.getId(), ajeno.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(dependenciasDe(a)).isEmpty();
    }

    @Test
    void changeStatusNoIniciaConUnaDependenciaPendiente() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());
        em.flush();

        assertThatThrownBy(() -> taskService.changeStatus(a.getId(), TaskStatus.IN_PROGRESS, propietario.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dependencias sin completar");
        assertThat(recargar(a).getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void changeStatusIniciaSiLasDependenciasEstanHechasOCanceladas() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());
        taskService.addDependency(a.getId(), c.getId(), propietario.getId());
        taskService.changeStatus(b.getId(), TaskStatus.DONE, propietario.getId());
        taskService.changeStatus(c.getId(), TaskStatus.CANCELLED, propietario.getId());
        em.flush();

        taskService.changeStatus(a.getId(), TaskStatus.IN_PROGRESS, propietario.getId());

        assertThat(recargar(a).getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateTaskNoIniciaConUnaDependenciaPendiente() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());
        em.flush();

        assertThatThrownBy(() -> editar(a, TaskStatus.IN_PROGRESS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dependencias sin completar");
        assertThat(recargar(a).getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void updateTaskSinCambiarElEstadoNoLoBloqueanLasDependencias() {
        Task enCurso = em.persist(task("En curso", propietario, TaskStatus.IN_PROGRESS));
        em.flush();
        taskService.addDependency(enCurso.getId(), b.getId(), propietario.getId());
        em.flush();

        editar(enCurso, TaskStatus.IN_PROGRESS);

        Task enBd = recargar(enCurso);
        assertThat(enBd.getTitle()).isEqualTo("En curso editada");
        assertThat(enBd.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void removeDependencyQuitaLaRelacionYLaRegistra() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());
        em.flush();

        taskService.removeDependency(a.getId(), b.getId(), propietario.getId());

        assertThat(dependenciasDe(a)).isEmpty();
        verify(activityLogService).log(propietario.getId(), ActionType.DEPENDENCY_REMOVED, "TASK",
                a.getId(), "A", "B", "");
    }

    @Test
    void getDependenciesSoloParaElPropietario() {
        taskService.addDependency(a.getId(), b.getId(), propietario.getId());
        em.flush();

        assertThat(taskService.getDependencies(a.getId(), propietario.getId()))
                .extracting(Task::getTitle).containsExactly("B");
        assertThatThrownBy(() -> taskService.getDependencies(a.getId(), ajeno.getId()))
                .isInstanceOf(BusinessException.class);
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private void editar(Task task, TaskStatus status) {
        taskService.updateTask(task.getId(), task.getTitle() + " editada", null, status,
                TaskPriority.MEDIUM, null, null, propietario.getId());
    }

    private java.util.List<String> dependenciasDe(Task task) {
        return recargar(task).getDependencies().stream().map(Task::getTitle).toList();
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

    private Task task(String title, User owner, TaskStatus status) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .priority(TaskPriority.MEDIUM)
                .status(status)
                .user(owner)
                .deleted(false)
                .build();
    }
}
