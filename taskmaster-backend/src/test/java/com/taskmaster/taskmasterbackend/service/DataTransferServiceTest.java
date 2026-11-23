package com.taskmaster.taskmasterbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taskmaster.taskmasterbackend.dto.response.ImportResultResponse;
import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.WorkLog;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests de {@link DataTransferService} y {@link DataImportWriter}: aislamiento entre cuentas
 * de la exportación, ida y vuelta de la importación aditiva y limpieza de referencias huérfanas.
 *
 * <p>Usa los repositorios reales contra la SQLite temporal que inyecta {@code validar.sh};
 * {@link ActivityLogService} es un mock. La escritura corre dentro de la transacción del test,
 * que equivale a la de {@code @Transactional} del bean; la atomicidad se prueba aparte en
 * {@link DataImportWriterAtomicityTest}.</p>
 *
 * @author Carlos
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataTransferServiceTest {

    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

    @Autowired
    private TestEntityManager em;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private WorkLogRepository workLogRepository;

    private ActivityLogService activityLog;
    private DataTransferService service;

    private User usuarioA;
    private User usuarioB;

    @BeforeEach
    void setUp() {
        activityLog = mock(ActivityLogService.class);
        DataImportWriter writer = new DataImportWriter(userRepository, projectRepository, taskRepository,
                workLogRepository, activityLog);
        service = new DataTransferService(projectRepository, taskRepository, workLogRepository, writer, MAPPER);

        usuarioA = em.persist(user("usuario-alfa"));
        usuarioB = em.persist(user("usuario-beta"));
        datosDeA();
        datosDeB();
        em.flush();
        em.clear();
    }

    // -------------------------------------------------------------------------
    // Exportación
    // -------------------------------------------------------------------------

    @Test
    void exportaSoloLosDatosDelUsuario() {
        String json = new String(service.exportJson(usuarioA.getId()), StandardCharsets.UTF_8);

        assertThat(json).contains("\"format\" : \"taskmaster-export\"", "ProyA", "TareaA1", "SubtareaA1",
                "PersonalA", "notaA");
        assertThat(json).doesNotContain("ProyB", "TareaB", "notaB");
        assertThat(json).doesNotContain("\"id\"", "\"user\"", "\"password\"", "\"email\"", "usuario-alfa",
                "usuario-beta");

        String jsonDeB = new String(service.exportJson(usuarioB.getId()), StandardCharsets.UTF_8);
        assertThat(jsonDeB).contains("ProyB", "TareaB", "notaB");
        assertThat(jsonDeB).doesNotContain("ProyA", "TareaA", "PersonalA", "notaA");
    }

    @Test
    void noExportaLaPapelera() {
        String json = new String(service.exportJson(usuarioA.getId()), StandardCharsets.UTF_8);

        assertThat(json).doesNotContain("TareaBorrada", "ProyectoBorrado", "TareaDeProyectoBorrado",
                "HijaDeBorrada", "PersonalBorrada");
        // Lo activo sigue ahí
        assertThat(json).contains("TareaA1", "TareaA2");
    }

    @Test
    void noExportaDependenciasQueCruzanUsuarios() throws IOException {
        long deA = idDeTarea("TareaA1");
        long deB = idDeTarea("TareaB");
        DataExportFile antesA = leer(service.exportJson(usuarioA.getId()));
        DataExportFile antesB = leer(service.exportJson(usuarioB.getId()));

        insertarDependencia(deA, deB);
        insertarDependencia(deB, deA);
        em.clear();

        DataExportFile despuesA = leer(service.exportJson(usuarioA.getId()));
        DataExportFile despuesB = leer(service.exportJson(usuarioB.getId()));
        assertThat(despuesA.dependencies()).isEqualTo(antesA.dependencies()).hasSize(2);
        assertThat(despuesB.dependencies()).isEqualTo(antesB.dependencies()).hasSize(1);
    }

    @Test
    void laConsultaDeDependenciasFiltraElDuenoDeLasDosPuntas() {
        long deA = idDeTarea("TareaA1");
        long deB = idDeTarea("TareaB");
        insertarDependencia(deA, deB);
        insertarDependencia(deB, deA);
        em.clear();

        // El servicio descarta además lo que no exporta, pero la consulta ya no debe devolver nada ajeno
        assertThat(taskRepository.findDependencyPairsByUserId(usuarioA.getId()))
                .extracting(par -> ((Number) par[1]).longValue())
                .doesNotContain(deB);
        assertThat(taskRepository.findDependencyPairsByUserId(usuarioB.getId()))
                .extracting(par -> ((Number) par[1]).longValue())
                .doesNotContain(deA);
        assertThat(taskRepository.findDependencyPairsByUserId(usuarioA.getId())).hasSize(2);
        assertThat(taskRepository.findDependencyPairsByUserId(usuarioB.getId())).hasSize(1);
    }

    @Test
    void elCsvSoloIncluyeLasTareasDelUsuario() {
        String csv = new String(service.exportCsv(usuarioA.getId(), true), StandardCharsets.UTF_8);

        assertThat(csv).contains("TareaA1", "SubtareaA1", "PersonalA");
        assertThat(csv).doesNotContain("TareaB", "ProyB", "TareaBorrada");
    }

    // -------------------------------------------------------------------------
    // Importación
    // -------------------------------------------------------------------------

    @Test
    void idaYVueltaReproduceLaEstructura() throws IOException {
        byte[] exportacionDeA = service.exportJson(usuarioA.getId());
        long proyectosDeA = projectRepository.findByUserIdAndDeletedFalse(usuarioA.getId()).size();
        long tareasDeA = taskRepository.findByUserIdAndDeletedFalse(usuarioA.getId()).size();
        List<Long> idsDeA = taskRepository.findByUserIdAndDeletedFalse(usuarioA.getId()).stream()
                .map(Task::getId).sorted().toList();

        ImportResultResponse resultado = service.importJson(usuarioB.getId(), new ByteArrayInputStream(exportacionDeA));
        em.flush();
        em.clear();

        assertThat(resultado.projects()).isEqualTo(1);
        assertThat(resultado.tasks()).isEqualTo(5);
        assertThat(resultado.workLogs()).isEqualTo(3);
        assertThat(resultado.dependencies()).isEqualTo(2);

        // B tiene lo suyo más las copias; todo lo importado es de B y con ids nuevos
        Project copia = projectRepository.findByUserIdAndDeletedFalse(usuarioB.getId()).stream()
                .filter(p -> p.getName().equals("ProyA")).findFirst().orElseThrow();
        assertThat(copia.getUser().getId()).isEqualTo(usuarioB.getId());
        assertThat(copia.getCategory()).isEqualTo(TaskCategory.ESTUDIOS);
        assertThat(copia.getEstimatedDuration()).isEqualByComparingTo("40");

        List<Task> copiasDeTareas = taskRepository.findByUserIdAndDeletedFalse(usuarioB.getId()).stream()
                .filter(t -> t.getTitle().contains("A1") || t.getTitle().contains("A2")
                        || t.getTitle().contains("PersonalA") || t.getTitle().contains("HijaPersonalA"))
                .toList();
        assertThat(copiasDeTareas).hasSize(5);
        assertThat(copiasDeTareas).allSatisfy(t -> {
            assertThat(t.getUser().getId()).isEqualTo(usuarioB.getId());
            assertThat(idsDeA).doesNotContain(t.getId());
        });
        Task tareaA1 = tarea(copiasDeTareas, "TareaA1");
        Task subtareaA1 = tarea(copiasDeTareas, "SubtareaA1");
        assertThat(tareaA1.getProject().getId()).isEqualTo(copia.getId());
        assertThat(subtareaA1.getParentTask().getId()).isEqualTo(tareaA1.getId());
        assertThat(subtareaA1.getProject().getId()).isEqualTo(copia.getId());
        assertThat(tarea(copiasDeTareas, "PersonalA").getProject()).isNull();
        assertThat(workLogRepository.findByTaskId(tareaA1.getId())).hasSize(2);
        assertThat(tareaA1.getDependencies()).extracting(Task::getTitle).containsExactly("TareaA2");

        // Lo de A no cambia
        assertThat(projectRepository.findByUserIdAndDeletedFalse(usuarioA.getId())).hasSize((int) proyectosDeA);
        assertThat(taskRepository.findByUserIdAndDeletedFalse(usuarioA.getId())).hasSize((int) tareasDeA);
        assertThat(taskRepository.findByUserIdAndDeletedFalse(usuarioA.getId()).stream().map(Task::getId).sorted()
                .toList()).isEqualTo(idsDeA);
    }

    @Test
    void importarEnUnaCuentaVaciaReproduceLaExportacionExacta() throws IOException {
        User vacio = em.persist(user("usuario-vacio"));
        em.flush();
        byte[] exportacionDeA = service.exportJson(usuarioA.getId());

        service.importJson(vacio.getId(), new ByteArrayInputStream(exportacionDeA));
        em.flush();
        em.clear();

        // Mismo árbol, mismas referencias y mismas dependencias (salvo las fechas informativas)
        assertThat(sinFechas(service.exportJson(vacio.getId()))).isEqualTo(sinFechas(exportacionDeA));
    }

    @Test
    void importarDosVecesDuplica() {
        byte[] exportacionDeA = service.exportJson(usuarioA.getId());
        int tareasDeB = taskRepository.findByUserIdAndDeletedFalse(usuarioB.getId()).size();

        service.importJson(usuarioB.getId(), new ByteArrayInputStream(exportacionDeA));
        service.importJson(usuarioB.getId(), new ByteArrayInputStream(exportacionDeA));
        em.flush();
        em.clear();

        List<Project> copias = projectRepository.findByUserIdAndDeletedFalse(usuarioB.getId()).stream()
                .filter(p -> p.getName().equals("ProyA")).toList();
        assertThat(copias).hasSize(2);
        assertThat(copias.get(0).getId()).isNotEqualTo(copias.get(1).getId());
        assertThat(taskRepository.findByUserIdAndDeletedFalse(usuarioB.getId())).hasSize(tareasDeB + 10);
    }

    @Test
    void importarNoTocaLosDatosExistentes() {
        List<String> antes = resumenDeTareas(usuarioB);
        List<String> proyectosAntes = projectRepository.findByUserIdAndDeletedFalse(usuarioB.getId()).stream()
                .map(p -> p.getId() + ":" + p.getName() + ":" + p.getStatus()).toList();

        service.importJson(usuarioB.getId(), new ByteArrayInputStream(service.exportJson(usuarioA.getId())));
        em.flush();
        em.clear();

        assertThat(resumenDeTareas(usuarioB)).containsAll(antes);
        assertThat(projectRepository.findByUserIdAndDeletedFalse(usuarioB.getId()).stream()
                .map(p -> p.getId() + ":" + p.getName() + ":" + p.getStatus()).toList())
                .containsAll(proyectosAntes);
        // Y la de B sigue siendo su dependencia, sin nada de A
        Task tareaB = taskRepository.findById(idDeTarea("TareaB")).orElseThrow();
        assertThat(tareaB.getDependencies()).extracting(Task::getTitle).containsExactly("TareaB2");
    }

    @Test
    void importarBorraAntesLasReferenciasHuerfanas() {
        // Filas que apuntan al id que va a recibir la primera tarea importada (los ids se reutilizan)
        long idLibre = maxTaskId() + 1;
        insertarRegistroHuerfano(idLibre);
        insertarDependencia(idLibre, idLibre + 999);
        em.clear();

        String json = """
                {"format":"taskmaster-export","version":1,"tasks":[{"title":"Nueva","workLogs":[
                  {"date":"2026-11-20","activityType":"TEST","hours":1}]}]}""";
        service.importJson(usuarioB.getId(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        em.flush();
        em.clear();

        assertThat(contar("SELECT COUNT(*) FROM work_logs WHERE note = 'huerfano'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM task_dependencies WHERE depends_on_task_id = " + (idLibre + 999)))
                .isZero();
        Task nueva = taskRepository.findByUserIdAndDeletedFalse(usuarioB.getId()).stream()
                .filter(t -> t.getTitle().equals("Nueva")).findFirst().orElseThrow();
        assertThat(nueva.getId()).isEqualTo(idLibre);
        assertThat(workLogRepository.findByTaskId(nueva.getId())).hasSize(1);
        assertThat(nueva.getDependencies()).isEmpty();
    }

    @Test
    void laCategoriaDeUnaTareaDeProyectoEsLaDelProyecto() {
        String json = """
                {"format":"taskmaster-export","version":1,"projects":[{"name":"Proy","category":"ESTUDIOS",
                  "tasks":[{"title":"Raiz","category":"TRABAJO","subtasks":[{"title":"Hija","category":"PERSONAL"}]}]}],
                 "tasks":[{"title":"Suelta","category":"TRABAJO","subtasks":[{"title":"HijaSuelta"}]},
                          {"title":"SinCategoria"}]}""";

        service.importJson(usuarioB.getId(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        em.flush();
        em.clear();

        List<Task> tareas = taskRepository.findByUserIdAndDeletedFalse(usuarioB.getId());
        assertThat(tarea(tareas, "Raiz").getCategory()).isEqualTo(TaskCategory.ESTUDIOS);
        assertThat(tarea(tareas, "Hija").getCategory()).isEqualTo(TaskCategory.ESTUDIOS);
        assertThat(tarea(tareas, "Suelta").getCategory()).isEqualTo(TaskCategory.TRABAJO);
        assertThat(tarea(tareas, "HijaSuelta").getCategory()).isEqualTo(TaskCategory.TRABAJO);
        assertThat(tarea(tareas, "SinCategoria").getCategory()).isEqualTo(TaskCategory.PERSONAL);
        assertThat(tarea(tareas, "SinCategoria").getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(tarea(tareas, "SinCategoria").getPriority()).isEqualTo(TaskPriority.MEDIUM);
    }

    @Test
    void laSubtareaHeredaElProyectoDeLaRaiz() {
        String json = """
                {"format":"taskmaster-export","version":1,"projects":[{"name":"Proy","category":"TRABAJO",
                  "tasks":[{"title":"Raiz","subtasks":[{"title":"Hija","subtasks":[{"title":"Nieta"}]}]}]}]}""";

        service.importJson(usuarioB.getId(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        em.flush();
        em.clear();

        List<Task> tareas = taskRepository.findByUserIdAndDeletedFalse(usuarioB.getId());
        Task nieta = tarea(tareas, "Nieta");
        assertThat(nieta.getProject().getName()).isEqualTo("Proy");
        assertThat(nieta.getParentTask().getTitle()).isEqualTo("Hija");
        assertThat(nieta.getParentTask().getParentTask().getTitle()).isEqualTo("Raiz");
        assertThat(nieta.getUser().getId()).isEqualTo(usuarioB.getId());
    }

    @Test
    void unFicheroNoValidoNoEscribeNada() {
        long proyectos = projectRepository.count();
        long tareas = taskRepository.count();
        long registros = workLogRepository.count();
        // Válido salvo el ciclo, que se detecta al final de la validación
        String json = """
                {"format":"taskmaster-export","version":1,"projects":[{"name":"Proy","category":"TRABAJO",
                  "tasks":[{"ref":"a","title":"A"},{"ref":"b","title":"B"}]}],
                 "dependencies":[{"task":"a","dependsOn":"b"},{"task":"b","dependsOn":"a"}]}""";

        assertThatThrownBy(() -> service.importJson(usuarioB.getId(),
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ciclo");
        assertThatThrownBy(() -> service.importJson(usuarioB.getId(), new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BusinessException.class).hasMessageContaining("vacío");

        assertThat(projectRepository.count()).isEqualTo(proyectos);
        assertThat(taskRepository.count()).isEqualTo(tareas);
        assertThat(workLogRepository.count()).isEqualTo(registros);
        verifyNoInteractions(activityLog);
    }

    @Test
    void registraActividadPorEntidadCreada() {
        String json = """
                {"format":"taskmaster-export","version":1,"projects":[
                  {"name":"P1","category":"TRABAJO","tasks":[{"title":"R1","subtasks":[{"title":"S1"},{"title":"S2"}]}]},
                  {"name":"P2","category":"TRABAJO"}],
                 "tasks":[{"title":"Suelta"}]}""";

        service.importJson(usuarioB.getId(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        Long b = usuarioB.getId();
        verify(activityLog, times(2)).log(eq(b), eq(ActionType.PROJECT_CREATED), eq("PROJECT"), anyLong(), anyString());
        verify(activityLog, times(2)).log(eq(b), eq(ActionType.TASK_CREATED), eq("TASK"), anyLong(), anyString());
        verify(activityLog, times(2)).log(eq(b), eq(ActionType.SUBTASK_CREATED), eq("SUBTASK"), anyLong(), anyString());
        verifyNoMoreActivity();
    }

    @Test
    void unCuerpoMayorQueElLimiteDa413() {
        DataTransferService pequeno = new DataTransferService(projectRepository, taskRepository, workLogRepository,
                mock(DataImportWriter.class), MAPPER, new DataImportValidator(MAPPER), 10);

        assertThatThrownBy(() -> pequeno.importJson(usuarioB.getId(),
                new ByteArrayInputStream("12345678901".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    // -------------------------------------------------------------------------
    // Datos de prueba
    // -------------------------------------------------------------------------

    /**
     * A: proyecto ProyA con TareaA1 (subtarea SubtareaA1, dos registros, depende de TareaA2) y
     * TareaA2; tarea personal PersonalA con subtarea HijaPersonalA que depende de TareaA1; y
     * una papelera (tarea, proyecto con tarea, subtarea de una tarea borrada, personal borrada).
     */
    private void datosDeA() {
        Project proyecto = em.persist(Project.builder().name("ProyA").description("descA")
                .category(TaskCategory.ESTUDIOS).status(TaskStatus.IN_PROGRESS).priority(TaskPriority.HIGH)
                .estimatedDuration(new BigDecimal("40.00")).user(usuarioA).deleted(false).build());

        Task a1 = em.persist(task("TareaA1", usuarioA, proyecto, null, TaskCategory.ESTUDIOS));
        Task s1 = em.persist(task("SubtareaA1", usuarioA, proyecto, a1, TaskCategory.ESTUDIOS));
        Task a2 = em.persist(task("TareaA2", usuarioA, proyecto, null, TaskCategory.ESTUDIOS));
        Task personal = em.persist(task("PersonalA", usuarioA, null, null, TaskCategory.PERSONAL));
        Task hija = em.persist(task("HijaPersonalA", usuarioA, null, personal, TaskCategory.PERSONAL));
        a1.getDependencies().add(a2);
        hija.getDependencies().add(a1);
        em.persist(workLog(a1, "2026-11-20", "2.50", "notaA"));
        em.persist(workLog(a1, "2026-11-19", "1.00", null));
        em.persist(workLog(s1, "2026-11-21", "0.50", "x"));

        Task borrada = task("TareaBorrada", usuarioA, proyecto, null, TaskCategory.ESTUDIOS);
        borrada.setDeleted(true);
        em.persist(borrada);
        em.persist(task("HijaDeBorrada", usuarioA, proyecto, borrada, TaskCategory.ESTUDIOS));
        Task personalBorrada = task("PersonalBorrada", usuarioA, null, null, TaskCategory.PERSONAL);
        personalBorrada.setDeleted(true);
        em.persist(personalBorrada);
        Project papelera = em.persist(Project.builder().name("ProyectoBorrado").category(TaskCategory.TRABAJO)
                .status(TaskStatus.TODO).priority(TaskPriority.LOW).user(usuarioA).deleted(true).build());
        em.persist(task("TareaDeProyectoBorrado", usuarioA, papelera, null, TaskCategory.TRABAJO));
    }

    /** B: proyecto ProyB con TareaB (un registro, depende de TareaB2) y TareaB2. */
    private void datosDeB() {
        Project proyecto = em.persist(Project.builder().name("ProyB").category(TaskCategory.TRABAJO)
                .status(TaskStatus.TODO).priority(TaskPriority.MEDIUM).user(usuarioB).deleted(false).build());
        Task b1 = em.persist(task("TareaB", usuarioB, proyecto, null, TaskCategory.TRABAJO));
        Task b2 = em.persist(task("TareaB2", usuarioB, proyecto, null, TaskCategory.TRABAJO));
        b1.getDependencies().add(b2);
        em.persist(workLog(b1, "2026-11-18", "3.00", "notaB"));
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private void verifyNoMoreActivity() {
        // Solo los tres tipos anteriores: no hay DEPENDENCY_ADDED ni entradas de otro tipo
        verify(activityLog, times(6)).log(anyLong(), org.mockito.ArgumentMatchers.any(ActionType.class),
                anyString(), anyLong(), anyString());
    }

    private static DataExportFile leer(byte[] json) throws IOException {
        return MAPPER.readValue(json, DataExportFile.class);
    }

    /** El JSON como árbol, sin las fechas que cambian al importar ({@code exportedAt}, {@code createdAt}). */
    private static JsonNode sinFechas(byte[] json) throws IOException {
        JsonNode raiz = MAPPER.readTree(json);
        quitarFechas(raiz);
        return raiz;
    }

    private static void quitarFechas(JsonNode nodo) {
        if (nodo instanceof ObjectNode objeto) {
            objeto.remove("exportedAt");
            objeto.remove("createdAt");
        }
        nodo.forEach(DataTransferServiceTest::quitarFechas);
    }

    private List<String> resumenDeTareas(User usuario) {
        return taskRepository.findByUserIdAndDeletedFalse(usuario.getId()).stream()
                .map(t -> t.getId() + ":" + t.getTitle() + ":" + t.getStatus() + ":"
                        + (t.getProject() == null ? "-" : t.getProject().getId()))
                .toList();
    }

    private static Task tarea(List<Task> tareas, String titulo) {
        return tareas.stream().filter(t -> t.getTitle().equals(titulo)).findFirst().orElseThrow();
    }

    private long idDeTarea(String titulo) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT id FROM tasks WHERE title = ?1")
                .setParameter(1, titulo).getSingleResult()).longValue();
    }

    private long maxTaskId() {
        return ((Number) em.getEntityManager().createNativeQuery("SELECT MAX(id) FROM tasks")
                .getSingleResult()).longValue();
    }

    private long contar(String sql) {
        return ((Number) em.getEntityManager().createNativeQuery(sql).getSingleResult()).longValue();
    }

    private void insertarDependencia(long taskId, long dependsOnId) {
        em.getEntityManager()
                .createNativeQuery("INSERT INTO task_dependencies (task_id, depends_on_task_id) VALUES (?1, ?2)")
                .setParameter(1, taskId)
                .setParameter(2, dependsOnId)
                .executeUpdate();
    }

    private void insertarRegistroHuerfano(long taskId) {
        em.getEntityManager()
                .createNativeQuery("INSERT INTO work_logs (date, activity_type, hours, note, task_id) "
                        + "VALUES ('2026-01-01', 'TEST', 9, 'huerfano', ?1)")
                .setParameter(1, taskId)
                .executeUpdate();
    }

    private static User user(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("x")
                .birthDate(LocalDate.of(2000, 1, 1))
                .build();
    }

    private static Task task(String title, User user, Project project, Task parent, TaskCategory category) {
        return Task.builder()
                .title(title)
                .category(category)
                .priority(TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .user(user)
                .project(project)
                .parentTask(parent)
                .deleted(false)
                .build();
    }

    private static WorkLog workLog(Task task, String fecha, String horas, String nota) {
        return WorkLog.builder()
                .task(task)
                .date(LocalDate.parse(fecha))
                .activityType(ActivityType.DEVELOPMENT)
                .hours(new BigDecimal(horas))
                .note(nota)
                .build();
    }
}
