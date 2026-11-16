package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.Project;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del ciclo de vida de una tarea en {@link TaskService} (CAL-1): creación
 * (valores por defecto, categoría y subtareas), papelera con borrado recursivo,
 * restauración, purga por antigüedad y vaciado de la papelera.
 *
 * <p>Usa el {@link TaskRepository} real contra la SQLite temporal que inyecta
 * {@code validar.sh}; {@link ProjectService}, {@link UserRepository} y
 * {@link ActivityLogService} son mocks.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskServiceLifecycleTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    private TaskService taskService;
    private ProjectService projectService;
    private ActivityLogService activityLogService;

    private User propietario;
    private User ajeno;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        projectService = mock(ProjectService.class);
        activityLogService = mock(ActivityLogService.class);
        taskService = new TaskService(taskRepository, projectService, userRepository, activityLogService);

        propietario = em.persist(user("propietario"));
        ajeno = em.persist(user("ajeno"));
        when(userRepository.findById(propietario.getId())).thenReturn(Optional.of(propietario));
        em.flush();
    }

    @Test
    void createTaskPersonalUsaLosValoresPorDefecto() {
        Task creada = taskService.createTask("Personal", null, null, null, null,
                null, null, null, propietario.getId());

        Task enBd = recargar(creada);
        assertThat(enBd.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(enBd.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(enBd.getCategory()).isEqualTo(TaskCategory.PERSONAL);
        assertThat(enBd.getProject()).isNull();
        assertThat(enBd.isDeleted()).isFalse();
        verify(activityLogService).log(propietario.getId(), ActionType.TASK_CREATED, "TASK",
                creada.getId(), "Personal");
    }

    @Test
    void createTaskDeProyectoHeredaLaCategoriaDelProyecto() {
        Project proyecto = em.persist(project("Trabajo", TaskCategory.TRABAJO));
        em.flush();
        when(projectService.getProjectByIdAndUser(proyecto.getId(), propietario.getId())).thenReturn(proyecto);

        Task creada = taskService.createTask("De proyecto", null, TaskPriority.HIGH, null, null,
                proyecto.getId(), null, TaskCategory.ESTUDIOS, propietario.getId());

        Task enBd = recargar(creada);
        assertThat(enBd.getCategory()).isEqualTo(TaskCategory.TRABAJO);
        assertThat(enBd.getProject().getId()).isEqualTo(proyecto.getId());
        assertThat(enBd.getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void createTaskConPadreCreaUnaSubtarea() {
        Task padre = em.persist(task("Padre", null, false));
        em.flush();

        Task creada = taskService.createTask("Hija", null, null, null, null,
                null, padre.getId(), null, propietario.getId());

        assertThat(recargar(creada).getParentTask().getId()).isEqualTo(padre.getId());
        verify(activityLogService).log(propietario.getId(), ActionType.SUBTASK_CREATED, "SUBTASK",
                creada.getId(), "Hija");
    }

    @Test
    void deleteTaskMandaALaPapeleraTodaLaJerarquia() {
        Task padre = em.persist(task("Padre", null, false));
        Task hija = em.persist(task("Hija", padre, false));
        Task nieta = em.persist(task("Nieta", hija, false));
        em.flush();

        taskService.deleteTask(padre.getId(), propietario.getId());

        for (Task t : new Task[]{padre, hija, nieta}) {
            Task enBd = recargar(t);
            assertThat(enBd.isDeleted()).as(t.getTitle()).isTrue();
            assertThat(enBd.getDeletedAt()).as(t.getTitle()).isNotNull();
        }
        verify(activityLogService).log(propietario.getId(), ActionType.TASK_DELETED, "TASK",
                padre.getId(), "Padre");
    }

    @Test
    void restoreTaskSoloRestauraDesdeLaPapeleraDelPropietario() {
        Task enPapelera = em.persist(task("En papelera", null, true));
        Task activa = em.persist(task("Activa", null, false));
        em.flush();

        assertThatThrownBy(() -> taskService.restoreTask(enPapelera.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> taskService.restoreTask(activa.getId(), propietario.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        taskService.restoreTask(enPapelera.getId(), propietario.getId());

        Task enBd = recargar(enPapelera);
        assertThat(enBd.isDeleted()).isFalse();
        assertThat(enBd.getDeletedAt()).isNull();
        verify(activityLogService).log(propietario.getId(), ActionType.TASK_RESTORED, "TASK",
                enPapelera.getId(), "En papelera");
    }

    @Test
    void purgeExpiredTasksSoloBorraLasCaducadasDelUsuario() {
        Task caducada = em.persist(borrada("Caducada", propietario, 31));
        Task reciente = em.persist(borrada("Reciente", propietario, 5));
        Task deOtro = em.persist(borrada("De otro", ajeno, 31));
        em.flush();

        taskService.purgeExpiredTasks(propietario.getId(), 30);
        em.flush();
        em.clear();

        assertThat(taskRepository.findById(caducada.getId())).isEmpty();
        assertThat(taskRepository.findById(reciente.getId())).isPresent();
        assertThat(taskRepository.findById(deOtro.getId())).isPresent();
    }

    @Test
    void emptyTrashSoloVaciaLaPapeleraDelUsuario() {
        Task enPapelera = em.persist(task("En papelera", null, true));
        Task activa = em.persist(task("Activa", null, false));
        Task deOtro = em.persist(borrada("De otro", ajeno, 1));
        em.flush();

        taskService.emptyTrash(propietario.getId());
        em.flush();
        em.clear();

        assertThat(taskRepository.findById(enPapelera.getId())).isEmpty();
        assertThat(taskRepository.findById(activa.getId())).isPresent();
        assertThat(taskRepository.findById(deOtro.getId())).isPresent();
        verify(activityLogService).log(eq(propietario.getId()), eq(ActionType.TASK_PERMANENTLY_DELETED),
                eq("TASK"), eq(enPapelera.getId()), anyString());
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

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

    private Project project(String name, TaskCategory category) {
        return Project.builder()
                .name(name)
                .category(category)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .user(propietario)
                .deleted(false)
                .build();
    }

    private Task task(String title, Task parent, boolean deleted) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .priority(TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .user(propietario)
                .parentTask(parent)
                .deleted(deleted)
                .deletedAt(deleted ? LocalDateTime.now() : null)
                .build();
    }

    private Task borrada(String title, User owner, int diasEnPapelera) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .priority(TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .user(owner)
                .deleted(true)
                .deletedAt(LocalDateTime.now().minusDays(diasEnPapelera))
                .build();
    }
}
