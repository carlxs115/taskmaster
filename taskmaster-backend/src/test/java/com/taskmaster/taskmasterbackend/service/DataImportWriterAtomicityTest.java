package com.taskmaster.taskmasterbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Test de atomicidad de {@link DataImportWriter}: si algo falla a mitad de una importación,
 * no queda nada escrito.
 *
 * <p>Corre sin la transacción del test ({@code NOT_SUPPORTED}) y envuelve la llamada en un
 * {@link TransactionTemplate}, que reproduce la transacción que abre {@code @Transactional}
 * en el bean real (un bean construido a mano no tiene proxy). Como sí confirma transacciones,
 * limpia en {@code @AfterEach} lo que crea. {@link #writeEsTransaccional()} garantiza que el
 * método sigue llevando la anotación, para que este test represente el comportamiento real.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataImportWriterAtomicityTest {

    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private WorkLogRepository workLogRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ActivityLogService activityLog;
    private DataImportWriter writer;
    private TransactionTemplate transaction;
    private User usuario;

    @BeforeEach
    void setUp() {
        activityLog = mock(ActivityLogService.class);
        writer = new DataImportWriter(userRepository, projectRepository, taskRepository, workLogRepository,
                activityLog);
        transaction = new TransactionTemplate(transactionManager);
        usuario = userRepository.save(User.builder()
                .username("usuario-atomico")
                .email("usuario-atomico@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build());
    }

    @AfterEach
    void limpiar() {
        // Por si una regresión dejara algo escrito: se borra todo lo del usuario de prueba
        transaction.executeWithoutResult(status -> {
            workLogRepository.deleteByTaskUserId(usuario.getId());
            taskRepository.deleteAll(taskRepository.findByUserIdAndDeletedFalse(usuario.getId()));
            projectRepository.deleteAll(projectRepository.findByUserIdAndDeletedFalse(usuario.getId()));
            userRepository.deleteById(usuario.getId());
            taskRepository.deleteOrphanTaskReferences();
        });
    }

    @Test
    void falloAMitadNoDejaNada() {
        // La tercera entrada de actividad (la subtarea) falla cuando ya se han escrito el proyecto,
        // la tarea, la subtarea y el registro de trabajo
        doNothing().doNothing().doThrow(new IllegalStateException("fallo simulado"))
                .when(activityLog).log(anyLong(), any(ActionType.class), anyString(), anyLong(), anyString());
        DataExportFile file = leer("""
                {"format":"taskmaster-export","version":1,"projects":[{"name":"P","category":"TRABAJO",
                  "tasks":[{"ref":"a","title":"A","workLogs":[{"date":"2026-11-20","activityType":"TEST","hours":1}],
                    "subtasks":[{"ref":"b","title":"B"}]}]}],
                 "dependencies":[{"task":"a","dependsOn":"b"}]}""");
        long proyectos = projectRepository.count();
        long tareas = taskRepository.count();
        long registros = workLogRepository.count();

        assertThatThrownBy(() -> transaction.execute(status -> writer.write(usuario.getId(), file)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(projectRepository.count()).isEqualTo(proyectos);
        assertThat(taskRepository.count()).isEqualTo(tareas);
        assertThat(workLogRepository.count()).isEqualTo(registros);
        assertThat(taskRepository.findByUserIdAndDeletedFalse(usuario.getId())).isEmpty();
        assertThat(taskRepository.findDependencyPairsByUserId(usuario.getId())).isEmpty();
    }

    @Test
    void sinFalloLaImportacionSeConfirma() {
        DataExportFile file = leer("""
                {"format":"taskmaster-export","version":1,"projects":[{"name":"P","category":"TRABAJO",
                  "tasks":[{"ref":"a","title":"A","subtasks":[{"ref":"b","title":"B"}]}]}],
                 "dependencies":[{"task":"a","dependsOn":"b"}]}""");

        transaction.execute(status -> writer.write(usuario.getId(), file));

        assertThat(taskRepository.findByUserIdAndDeletedFalse(usuario.getId())).hasSize(2);
        assertThat(taskRepository.findDependencyPairsByUserId(usuario.getId())).hasSize(1);
    }

    @Test
    void writeEsTransaccional() throws NoSuchMethodException {
        assertThat(DataImportWriter.class
                .getMethod("write", Long.class, DataExportFile.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static DataExportFile leer(String json) {
        DataImportValidator validator = new DataImportValidator(MAPPER);
        DataExportFile file = validator.parse(json.getBytes(StandardCharsets.UTF_8));
        validator.validate(file);
        return file;
    }
}
