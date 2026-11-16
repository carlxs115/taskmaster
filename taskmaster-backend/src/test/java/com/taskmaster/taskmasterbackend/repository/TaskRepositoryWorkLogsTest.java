package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.WorkLog;
import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
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

/**
 * Test de regresión de {@link TaskRepository#deleteOrphanWorkLogs()} y de
 * {@link TaskRepository#deleteOrphanTaskReferences()} (SEG-9): qué registros de
 * trabajo borran y cuáles respetan.
 *
 * <p>Los registros huérfanos se insertan con SQL nativo, posible porque el esquema
 * SQLite no tiene claves foráneas. Usa la SQLite temporal que inyecta
 * {@code validar.sh}.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryWorkLogsTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    private Task activa;
    private Task enPapelera;
    private long validoActiva;
    private long validoPapelera;

    @BeforeEach
    void setUp() {
        User uno = em.persist(user("uno"));
        activa = em.persist(task("Activa", uno, false));
        // Válido aunque la tarea esté en la papelera: se restaura con ella
        enPapelera = em.persist(task("En papelera", uno, true));
        validoActiva = em.persist(registro(activa)).getId();
        validoPapelera = em.persist(registro(enPapelera)).getId();
        em.flush();

        long inexistente = maxTaskId() + 1000;
        insertarRegistro(inexistente);
        insertarRegistro(inexistente + 1);
        em.clear();
    }

    @Test
    void deleteOrphanWorkLogsBorraSoloLosDeTareasInexistentes() {
        assertThat(ids()).hasSize(4);

        assertThat(taskRepository.deleteOrphanWorkLogs()).isEqualTo(2);
        assertThat(ids()).containsExactlyInAnyOrder(validoActiva, validoPapelera);

        // Idempotente: una segunda llamada no encuentra nada que borrar
        assertThat(taskRepository.deleteOrphanWorkLogs()).isZero();
        assertThat(ids()).containsExactlyInAnyOrder(validoActiva, validoPapelera);
    }

    @Test
    void deleteOrphanTaskReferencesLimpiaDependenciasYRegistros() {
        long inexistente = maxTaskId() + 1000;
        insertarDependencia(activa.getId(), inexistente);
        insertarDependencia(activa.getId(), enPapelera.getId()); // válida
        em.clear();

        taskRepository.deleteOrphanTaskReferences();

        assertThat(ids()).containsExactlyInAnyOrder(validoActiva, validoPapelera);
        assertThat(dependencias()).containsExactly(activa.getId() + "->" + enPapelera.getId());
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private long maxTaskId() {
        Object max = em.getEntityManager().createNativeQuery("SELECT MAX(id) FROM tasks").getSingleResult();
        return ((Number) max).longValue();
    }

    private void insertarRegistro(long taskId) {
        em.getEntityManager()
                .createNativeQuery("INSERT INTO work_logs (date, activity_type, hours, note, task_id) "
                        + "VALUES ('2026-11-16', 'DEVELOPMENT', 2.5, 'huérfano', ?1)")
                .setParameter(1, taskId)
                .executeUpdate();
    }

    private void insertarDependencia(long taskId, long dependsOnId) {
        em.getEntityManager()
                .createNativeQuery("INSERT INTO task_dependencies (task_id, depends_on_task_id) VALUES (?1, ?2)")
                .setParameter(1, taskId)
                .setParameter(2, dependsOnId)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids() {
        List<Object> filas = em.getEntityManager()
                .createNativeQuery("SELECT id FROM work_logs")
                .getResultList();
        return filas.stream().map(f -> ((Number) f).longValue()).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> dependencias() {
        List<Object[]> filas = em.getEntityManager()
                .createNativeQuery("SELECT task_id, depends_on_task_id FROM task_dependencies")
                .getResultList();
        return filas.stream()
                .map(f -> ((Number) f[0]).longValue() + "->" + ((Number) f[1]).longValue())
                .toList();
    }

    private static WorkLog registro(Task task) {
        return WorkLog.builder()
                .date(LocalDate.of(2026, 11, 16))
                .activityType(ActivityType.DEVELOPMENT)
                .hours(new BigDecimal("2.5"))
                .note("válido")
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

    private static Task task(String title, User user, boolean deleted) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .priority(TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .user(user)
                .deleted(deleted)
                .build();
    }
}
