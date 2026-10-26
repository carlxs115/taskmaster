package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de regresión de {@link TaskRepository#findActiveRootTasksForCalendar}.
 *
 * <p>Usa la SQLite temporal que inyecta {@code validar.sh} (no hay H2 en el POM),
 * por eso no se reemplaza la base de datos de pruebas.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryCalendarTest {

    private static final LocalDate START = LocalDate.of(2026, 2, 1);
    private static final LocalDate END   = LocalDate.of(2026, 2, 28);
    private static final LocalDate DUE   = LocalDate.of(2026, 2, 15);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void excluyeTareasDeProyectosEnLaPapelera() {
        User user = em.persist(User.builder()
                .username("calendario")
                .email("calendario@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build());

        Project activo = em.persist(project(user, "Activo", false));
        Project borrado = em.persist(project(user, "Borrado", true));

        Task personal  = em.persist(task(user, null, "Personal"));
        Task deActivo  = em.persist(task(user, activo, "De proyecto activo"));
        Task deBorrado = em.persist(task(user, borrado, "De proyecto borrado"));

        Task subtarea = task(user, activo, "Subtarea");
        subtarea.setParentTask(deActivo);
        em.persist(subtarea);

        Task eliminada = task(user, null, "Eliminada");
        eliminada.setDeleted(true);
        em.persist(eliminada);

        em.flush();
        em.clear();

        List<Task> result = taskRepository.findActiveRootTasksForCalendar(user.getId(), START, END);

        assertThat(result)
                .extracting(Task::getId)
                .containsExactlyInAnyOrder(personal.getId(), deActivo.getId())
                .doesNotContain(deBorrado.getId());
    }

    private static Project project(User user, String name, boolean deleted) {
        return Project.builder()
                .name(name)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .category(TaskCategory.PERSONAL)
                .user(user)
                .deleted(deleted)
                .build();
    }

    private static Task task(User user, Project project, String title) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .dueDate(DUE)
                .user(user)
                .project(project)
                .build();
    }
}
