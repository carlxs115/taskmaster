package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de las reglas de negocio de {@link ProjectService} (CAL-1): creación,
 * edición, regla de completar, papelera, restauración, borrado definitivo y purga.
 *
 * <p>Usa los repositorios reales contra la SQLite temporal que inyecta
 * {@code validar.sh}; {@link UserService} y {@link ActivityLogService} son mocks.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    private ProjectService projectService;
    private ActivityLogService activityLogService;

    private User propietario;
    private User ajeno;

    @BeforeEach
    void setUp() {
        UserService userService = mock(UserService.class);
        activityLogService = mock(ActivityLogService.class);
        projectService = new ProjectService(projectRepository, userService, activityLogService, taskRepository);

        propietario = em.persist(user("propietario"));
        ajeno = em.persist(user("ajeno"));
        when(userService.findById(propietario.getId())).thenReturn(propietario);
        em.flush();
    }

    @Test
    void createProjectSinEstadoNiPrioridadUsaTodoYMedium() {
        Project creado = projectService.createProject("Nuevo", "desc", TaskCategory.TRABAJO,
                null, null, null, propietario.getId());

        Project enBd = recargar(creado);
        assertThat(enBd.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(enBd.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(enBd.getCategory()).isEqualTo(TaskCategory.TRABAJO);
        assertThat(enBd.isDeleted()).isFalse();
        assertThat(enBd.getUser().getId()).isEqualTo(propietario.getId());
        verify(activityLogService).log(eq(propietario.getId()), eq(ActionType.PROJECT_CREATED),
                eq("PROJECT"), eq(creado.getId()), eq("Nuevo"));
    }

    @Test
    void getProjectByIdAndUserSoloDevuelveLosActivosDelUsuario() {
        Project propio = em.persist(project("Propio", propietario, false, null));
        Project borrado = em.persist(project("Borrado", propietario, true, LocalDateTime.now()));
        em.flush();

        assertThat(projectService.getProjectByIdAndUser(propio.getId(), propietario.getId()).getName())
                .isEqualTo("Propio");
        assertThatThrownBy(() -> projectService.getProjectByIdAndUser(propio.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> projectService.getProjectByIdAndUser(borrado.getId(), propietario.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProjectNoCompletaConUnaTareaPendiente() {
        Project proyecto = em.persist(project("Con pendientes", propietario, false, null));
        em.persist(task("Pendiente", proyecto, TaskStatus.TODO, false));
        em.flush();

        assertThatThrownBy(() -> completar(proyecto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tareas pendientes");

        assertThat(recargar(proyecto).getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void updateProjectCompletaSiLasTareasEstanHechasCanceladasOEnLaPapelera() {
        Project proyecto = em.persist(project("Terminado", propietario, false, null));
        em.persist(task("Hecha", proyecto, TaskStatus.DONE, false));
        em.persist(task("Cancelada", proyecto, TaskStatus.CANCELLED, false));
        em.persist(task("En la papelera", proyecto, TaskStatus.TODO, true));
        em.flush();

        completar(proyecto);

        assertThat(recargar(proyecto).getStatus()).isEqualTo(TaskStatus.DONE);
        verify(activityLogService).log(propietario.getId(), ActionType.PROJECT_STATUS_CHANGED, "PROJECT",
                proyecto.getId(), "Terminado", "TODO", "DONE");
    }

    @Test
    void updateProjectSinCambiarElEstadoRegistraEdicion() {
        Project proyecto = em.persist(project("Antes", propietario, false, null));
        em.flush();

        projectService.updateProject(proyecto.getId(), "Después", "nueva", TaskCategory.ESTUDIOS,
                TaskStatus.TODO, TaskPriority.HIGH, null, propietario.getId());

        Project enBd = recargar(proyecto);
        assertThat(enBd.getName()).isEqualTo("Después");
        assertThat(enBd.getCategory()).isEqualTo(TaskCategory.ESTUDIOS);
        assertThat(enBd.getPriority()).isEqualTo(TaskPriority.HIGH);
        verify(activityLogService).log(propietario.getId(), ActionType.PROJECT_EDITED, "PROJECT",
                proyecto.getId(), "Después");
    }

    @Test
    void updateProjectDeOtroUsuarioNoSeAplica() {
        Project proyecto = em.persist(project("Ajeno", propietario, false, null));
        em.flush();

        assertThatThrownBy(() -> projectService.updateProject(proyecto.getId(), "Cambiado", null,
                TaskCategory.PERSONAL, TaskStatus.TODO, TaskPriority.LOW, null, ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(recargar(proyecto).getName()).isEqualTo("Ajeno");
    }

    @Test
    void deleteProjectLoMandaALaPapeleraSoloAlPropietario() {
        Project proyecto = em.persist(project("A borrar", propietario, false, null));
        em.flush();

        assertThatThrownBy(() -> projectService.deleteProject(proyecto.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(recargar(proyecto).isDeleted()).isFalse();

        projectService.deleteProject(proyecto.getId(), propietario.getId());

        Project enBd = recargar(proyecto);
        assertThat(enBd.isDeleted()).isTrue();
        assertThat(enBd.getDeletedAt()).isNotNull();
        verify(activityLogService).log(propietario.getId(), ActionType.PROJECT_DELETED, "PROJECT",
                proyecto.getId(), "A borrar");
    }

    @Test
    void restoreProjectSoloRestauraDesdeLaPapeleraDelPropietario() {
        Project enPapelera = em.persist(project("En papelera", propietario, true, LocalDateTime.now()));
        Project activo = em.persist(project("Activo", propietario, false, null));
        em.flush();

        assertThatThrownBy(() -> projectService.restoreProject(enPapelera.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> projectService.restoreProject(activo.getId(), propietario.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        projectService.restoreProject(enPapelera.getId(), propietario.getId());

        Project enBd = recargar(enPapelera);
        assertThat(enBd.isDeleted()).isFalse();
        assertThat(enBd.getDeletedAt()).isNull();
    }

    @Test
    void deletePermanentlyDeOtroUsuarioNoBorraNada() {
        Project proyecto = em.persist(project("En papelera", propietario, true, LocalDateTime.now()));
        Task tarea = em.persist(task("Tarea", proyecto, TaskStatus.TODO, false));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> projectService.deletePermanently(proyecto.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Proyecto no encontrado en la papelera");
        em.flush();
        em.clear();

        assertThat(projectRepository.findById(proyecto.getId())).isPresent();
        assertThat(taskRepository.findById(tarea.getId())).isPresent();
        verify(activityLogService, never()).log(anyLong(), eq(ActionType.PROJECT_PERMANENTLY_DELETED),
                anyString(), anyLong(), anyString());
    }

    @Test
    void deletePermanentlyNoBorraUnProyectoActivo() {
        Project activo = em.persist(project("Activo", propietario, false, null));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> projectService.deletePermanently(activo.getId(), propietario.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(recargar(activo).isDeleted()).isFalse();
    }

    @Test
    void deletePermanentlyDeIdInexistenteDa404() {
        assertThatThrownBy(() -> projectService.deletePermanently(Long.MAX_VALUE, propietario.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletePermanentlyBorraElProyectoDeLaPapeleraConSusTareas() {
        Project proyecto = em.persist(project("Para siempre", propietario, true, LocalDateTime.now()));
        Task tarea = em.persist(task("Tarea", proyecto, TaskStatus.TODO, false));
        em.flush();
        em.clear();

        projectService.deletePermanently(proyecto.getId(), propietario.getId());
        em.flush();
        em.clear();

        assertThat(projectRepository.findById(proyecto.getId())).isEmpty();
        assertThat(taskRepository.findById(tarea.getId())).isEmpty();
        verify(activityLogService).log(propietario.getId(), ActionType.PROJECT_PERMANENTLY_DELETED, "PROJECT",
                proyecto.getId(), "Para siempre");
    }

    @Test
    void purgeExpiredProjectsSoloBorraLosCaducadosDelUsuario() {
        Project caducado = em.persist(project("Caducado", propietario, true, LocalDateTime.now().minusDays(31)));
        Project reciente = em.persist(project("Reciente", propietario, true, LocalDateTime.now().minusDays(5)));
        Project deOtro = em.persist(project("De otro", ajeno, true, LocalDateTime.now().minusDays(31)));
        em.flush();

        projectService.purgeExpiredProjects(propietario.getId(), 30);
        em.flush();
        em.clear();

        assertThat(projectRepository.findById(caducado.getId())).isEmpty();
        assertThat(projectRepository.findById(reciente.getId())).isPresent();
        assertThat(projectRepository.findById(deOtro.getId())).isPresent();
    }

    @Test
    void emptyTrashSoloVaciaLaPapeleraDelUsuario() {
        Project enPapelera = em.persist(project("En papelera", propietario, true, LocalDateTime.now()));
        Project activo = em.persist(project("Activo", propietario, false, null));
        Project deOtro = em.persist(project("De otro", ajeno, true, LocalDateTime.now()));
        em.flush();

        projectService.emptyTrash(propietario.getId());
        em.flush();
        em.clear();

        assertThat(projectRepository.findById(enPapelera.getId())).isEmpty();
        assertThat(projectRepository.findById(activo.getId())).isPresent();
        assertThat(projectRepository.findById(deOtro.getId())).isPresent();
        verify(activityLogService).log(eq(propietario.getId()), eq(ActionType.PROJECT_PERMANENTLY_DELETED),
                eq("PROJECT"), eq(enPapelera.getId()), anyString());
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private void completar(Project proyecto) {
        projectService.updateProject(proyecto.getId(), proyecto.getName(), null, proyecto.getCategory(),
                TaskStatus.DONE, proyecto.getPriority(), null, propietario.getId());
    }

    private Project recargar(Project project) {
        em.flush();
        em.clear();
        return projectRepository.findById(project.getId()).orElseThrow();
    }

    private User user(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
    }

    private Project project(String name, User owner, boolean deleted, LocalDateTime deletedAt) {
        return Project.builder()
                .name(name)
                .category(TaskCategory.PERSONAL)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .user(owner)
                .deleted(deleted)
                .deletedAt(deletedAt)
                .build();
    }

    private Task task(String title, Project project, TaskStatus status, boolean deleted) {
        return Task.builder()
                .title(title)
                .category(project.getCategory())
                .priority(TaskPriority.MEDIUM)
                .status(status)
                .user(project.getUser())
                .project(project)
                .deleted(deleted)
                .deletedAt(deleted ? LocalDateTime.now() : null)
                .build();
    }
}
