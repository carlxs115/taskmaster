package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.WorkLogDTO;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.WorkLog;
import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de regresión de SEG-9: solo el propietario de una tarea puede leer sus
 * registros de trabajo y su total de horas; si la tarea es de otro usuario se
 * responde igual que si no existiera.
 *
 * <p>Usa los repositorios reales contra la SQLite temporal que inyecta
 * {@code validar.sh}.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkLogServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    private WorkLogService workLogService;

    private User propietario;
    private User ajeno;
    private Task padre;

    @BeforeEach
    void setUp() {
        workLogService = new WorkLogService(workLogRepository, taskRepository);

        propietario = em.persist(user("propietario"));
        ajeno = em.persist(user("ajeno"));
        padre = em.persist(task("Padre", null, propietario));
        Task subtarea = em.persist(task("Subtarea", padre, propietario));
        em.persist(registro(padre, "2.5", "nota del padre"));
        em.persist(registro(subtarea, "1.5", "nota de la subtarea"));
        em.flush();
        em.clear();
    }

    @Test
    void getByTaskDeOtroUsuarioLanzaNoEncontrada() {
        assertThatThrownBy(() -> workLogService.getByTask(padre.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTotalHoursDeOtroUsuarioLanzaNoEncontrada() {
        assertThatThrownBy(() -> workLogService.getTotalHours(padre.getId(), ajeno.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tareaAjenaYTareaInexistenteDanElMismoMensaje() {
        long inexistente = maxTaskId() + 1000;

        assertThatThrownBy(() -> workLogService.getByTask(padre.getId(), ajeno.getId()))
                .hasMessage("Tarea no encontrada con id: " + padre.getId());
        assertThatThrownBy(() -> workLogService.getByTask(inexistente, ajeno.getId()))
                .hasMessage("Tarea no encontrada con id: " + inexistente);
        assertThatThrownBy(() -> workLogService.getTotalHours(inexistente, ajeno.getId()))
                .hasMessage("Tarea no encontrada con id: " + inexistente);
    }

    @Test
    void getByTaskDelPropietarioDevuelveSusRegistros() {
        List<WorkLogDTO> registros = workLogService.getByTask(padre.getId(), propietario.getId());

        assertThat(registros).hasSize(1);
        assertThat(registros.get(0).note()).isEqualTo("nota del padre");
        assertThat(registros.get(0).hours()).isEqualByComparingTo(new BigDecimal("2.5"));
    }

    @Test
    void getTotalHoursDelPropietarioSumaLasSubtareas() {
        assertThat(workLogService.getTotalHours(padre.getId(), propietario.getId()))
                .isEqualByComparingTo(new BigDecimal("4"));
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private long maxTaskId() {
        Object max = em.getEntityManager().createNativeQuery("SELECT MAX(id) FROM tasks").getSingleResult();
        return ((Number) max).longValue();
    }

    private static WorkLog registro(Task task, String horas, String nota) {
        return WorkLog.builder()
                .date(LocalDate.of(2026, 11, 16))
                .activityType(ActivityType.DEVELOPMENT)
                .hours(new BigDecimal(horas))
                .note(nota)
                .task(task)
                .build();
    }

    private static User user(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
    }

    private static Task task(String title, Task parent, User user) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .priority(TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .user(user)
                .parentTask(parent)
                .build();
    }
}
