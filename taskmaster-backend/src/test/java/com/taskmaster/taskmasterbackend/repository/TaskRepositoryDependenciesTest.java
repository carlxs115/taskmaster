package com.taskmaster.taskmasterbackend.repository;

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
 * Test de regresión de {@link TaskRepository#deleteInvalidDependencies()} (SEG-8):
 * qué filas de {@code task_dependencies} borra y cuáles respeta.
 *
 * <p>Las filas inválidas se insertan con SQL nativo, posible porque el esquema SQLite
 * no tiene claves foráneas. Usa la SQLite temporal que inyecta {@code validar.sh}.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryDependenciesTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void borraLasFilasQueApuntanATareasInexistentesOEntreUsuarios() {
        User uno = em.persist(user("uno"));
        User dos = em.persist(user("dos"));

        Task a = em.persist(task("A", uno));
        Task b = em.persist(task("B", uno));
        Task deDos = em.persist(task("De dos", dos));
        Task enPapelera = task("En papelera", uno);
        enPapelera.setDeleted(true);
        em.persist(enPapelera);
        a.getDependencies().add(b); // válida: A → B, mismo usuario
        em.flush();
        // Válida aunque la predecesora esté en la papelera: hace falta para restaurarla
        insertarDependencia(a.getId(), enPapelera.getId());

        long inexistente = maxTaskId() + 1000;
        insertarDependencia(a.getId(), inexistente);      // huérfana por predecesora
        insertarDependencia(inexistente, b.getId());      // huérfana por tarea
        insertarDependencia(a.getId(), deDos.getId());    // cruzada entre usuarios
        em.clear();

        assertThat(filas()).hasSize(5);

        String[] validas = {a.getId() + "->" + b.getId(), a.getId() + "->" + enPapelera.getId()};
        assertThat(taskRepository.deleteInvalidDependencies()).isEqualTo(3);
        assertThat(filas()).containsExactlyInAnyOrder(validas);

        // Idempotente: una segunda llamada no encuentra nada que borrar
        assertThat(taskRepository.deleteInvalidDependencies()).isZero();
        assertThat(filas()).containsExactlyInAnyOrder(validas);
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private long maxTaskId() {
        Object max = em.getEntityManager().createNativeQuery("SELECT MAX(id) FROM tasks").getSingleResult();
        return ((Number) max).longValue();
    }

    private void insertarDependencia(long taskId, long dependsOnId) {
        em.getEntityManager()
                .createNativeQuery("INSERT INTO task_dependencies (task_id, depends_on_task_id) VALUES (?1, ?2)")
                .setParameter(1, taskId)
                .setParameter(2, dependsOnId)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<String> filas() {
        List<Object[]> filas = em.getEntityManager()
                .createNativeQuery("SELECT task_id, depends_on_task_id FROM task_dependencies")
                .getResultList();
        return filas.stream()
                .map(f -> ((Number) f[0]).longValue() + "->" + ((Number) f[1]).longValue())
                .toList();
    }

    private static User user(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
    }

    private static Task task(String title, User user) {
        return Task.builder()
                .title(title)
                .category(TaskCategory.PERSONAL)
                .priority(TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .user(user)
                .build();
    }
}
