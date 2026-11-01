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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de regresión de {@link TaskRepository#countActiveTasks} y
 * {@link TaskRepository#countActiveTasksByStatus}.
 *
 * <p>Usa la SQLite temporal que inyecta {@code validar.sh} (no hay H2 en el POM),
 * por eso no se reemplaza la base de datos de pruebas.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryStatsTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void excluyeTareasDeProyectosEnLaPapelera() {
        User user = em.persist(user("estadisticas"));
        User otro = em.persist(user("otro"));

        Project activo = em.persist(project(user, "Activo", false));
        Project borrado = em.persist(project(user, "Borrado", true));

        em.persist(task(user, null, "Personal", TaskStatus.TODO));
        em.persist(task(user, activo, "De proyecto activo", TaskStatus.DONE));
        em.persist(task(user, borrado, "De proyecto borrado hecha", TaskStatus.DONE));
        em.persist(task(user, borrado, "De proyecto borrado pendiente", TaskStatus.TODO));

        Task eliminada = task(user, null, "Eliminada", TaskStatus.TODO);
        eliminada.setDeleted(true);
        em.persist(eliminada);

        em.persist(task(otro, null, "De otro usuario", TaskStatus.TODO));

        em.flush();
        em.clear();

        assertThat(taskRepository.countActiveTasks(user.getId())).isEqualTo(2);
        assertThat(taskRepository.countActiveTasksByStatus(user.getId(), TaskStatus.DONE)).isEqualTo(1);
        assertThat(taskRepository.countActiveTasksByStatus(user.getId(), TaskStatus.TODO)).isEqualTo(1);
    }

    private static User user(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
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

    private static Task task(User user, Project project, String title, TaskStatus status) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .status(status)
                .user(user)
                .project(project)
                .build();
    }
}
