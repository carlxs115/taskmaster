package com.taskmaster.taskmasterbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link DataImportValidator}: lectura estricta y validación completa del fichero
 * de importación, con la ruta del fallo en el mensaje. No usa Spring ni base de datos.
 *
 * @author Carlos
 */
class DataImportValidatorTest {

    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

    private final DataImportValidator validator = new DataImportValidator(MAPPER);

    // -------------------------------------------------------------------------
    // Casos válidos
    // -------------------------------------------------------------------------

    @Test
    void aceptaUnaExportacionCompleta() {
        String json = fichero("""
                [{"name":"TFC","description":"Memoria","status":"IN_PROGRESS","priority":"HIGH",
                  "category":"ESTUDIOS","estimatedDuration":40.00,"createdAt":"2026-10-01T09:00:00",
                  "tasks":[{"ref":"a","title":"A","dueDate":"2026-12-15","estimatedDuration":10.5,
                    "workLogs":[{"date":"2026-11-20","activityType":"DOCUMENTATION","hours":2.50,"note":"x"}],
                    "subtasks":[{"ref":"b","title":"B","subtasks":[{"ref":"c","title":"C","subtasks":[]}]}]}]}]
                """,
                """
                [{"ref":"p","title":"Personal","category":"PERSONAL"}]
                """,
                """
                [{"task":"a","dependsOn":"b"},{"task":"c","dependsOn":"b"}]
                """);

        DataExportFile file = validator.parse(json.getBytes());
        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();

        assertThat(file.projects()).hasSize(1);
        assertThat(file.projects().get(0).tasks().get(0).subtasks().get(0).subtasks()).hasSize(1);
        assertThat(file.dependencies()).hasSize(2);
    }

    @Test
    void dependenciasDuplicadasSeIgnoran() {
        String json = fichero("[{\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":["
                        + "{\"ref\":\"a\",\"title\":\"A\"},{\"ref\":\"b\",\"title\":\"B\"}]}]", null,
                "[{\"task\":\"a\",\"dependsOn\":\"b\"},{\"task\":\"a\",\"dependsOn\":\"b\"}]");

        assertThatCode(() -> aceptar(validator, json)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Lectura estricta
    // -------------------------------------------------------------------------

    @Test
    void rechazaUnJsonMalFormado() {
        rechaza("{\"format\":", "no es un JSON válido");
        rechaza("esto no es json", "no es un JSON válido");
    }

    @Test
    void rechazaContenidoTrasElJson() {
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\"") + " {}", "formato esperado");
    }

    @Test
    void rechazaUnaPropiedadDesconocidaConSuRuta() {
        rechaza(conProyecto("\"id\":5,\"name\":\"P\",\"category\":\"TRABAJO\""), "projects[0].id");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"T\",\"userId\":1}]"),
                "projects[0].tasks[0].userId");
    }

    @Test
    void rechazaUnaClaveDuplicada() {
        rechaza("{\"format\":\"taskmaster-export\",\"format\":\"otro\",\"version\":1}", "repetida");
    }

    @Test
    void rechazaUnEnumDesconocidoConSuRuta() {
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"A\"},"
                + "{\"title\":\"B\"},{\"title\":\"C\",\"status\":\"FOO\"}]"), "projects[0].tasks[2].status");
    }

    @Test
    void rechazaUnEnumNumerico() {
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"A\",\"status\":0}]"),
                "projects[0].tasks[0].status");
    }

    // -------------------------------------------------------------------------
    // Cabecera
    // -------------------------------------------------------------------------

    @Test
    void rechazaUnFormatoOUnaVersionQueNoSonLosEsperados() {
        String proyectos = "\"projects\":[{\"name\":\"P\",\"category\":\"TRABAJO\"}]";
        rechaza("{\"format\":\"otro\",\"version\":1," + proyectos + "}", "no es una exportación de TaskMaster");
        rechaza("{\"version\":1," + proyectos + "}", "no es una exportación de TaskMaster");
        rechaza("{\"format\":\"taskmaster-export\",\"version\":2," + proyectos + "}", "Versión del archivo no compatible");
        rechaza("{\"format\":\"taskmaster-export\"," + proyectos + "}", "Versión del archivo no compatible");
    }

    @Test
    void rechazaUnFicheroSinProyectosNiTareas() {
        rechaza("{\"format\":\"taskmaster-export\",\"version\":1}", "no contiene proyectos ni tareas");
        rechaza("{\"format\":\"taskmaster-export\",\"version\":1,\"projects\":[],\"tasks\":[]}",
                "no contiene proyectos ni tareas");
    }

    @Test
    void rechazaUnElementoNuloDentroDeUnaLista() {
        rechaza("{\"format\":\"taskmaster-export\",\"version\":1,\"projects\":[null]}", "projects[0]");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"A\"},null]"),
                "projects[0].tasks[1]");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"A\","
                + "\"workLogs\":[null]}]"), "projects[0].tasks[0].workLogs[0]");
    }

    // -------------------------------------------------------------------------
    // Campos
    // -------------------------------------------------------------------------

    @Test
    void rechazaNombresYTitulosVaciosOSoloEspacios() {
        rechaza(conProyecto("\"name\":\"\",\"category\":\"TRABAJO\""), "projects[0].name");
        rechaza(conProyecto("\"category\":\"TRABAJO\""), "projects[0].name");
        rechaza(conProyecto("\"name\":\"   \",\"category\":\"TRABAJO\""), "projects[0].name");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"  \"}]"),
                "projects[0].tasks[0].title");
        rechaza("{\"format\":\"taskmaster-export\",\"version\":1,\"tasks\":[{\"description\":\"x\"}]}",
                "tasks[0].title");
    }

    @Test
    void rechazaUnProyectoSinCategoria() {
        rechaza(conProyecto("\"name\":\"P\""), "projects[0].category");
    }

    @Test
    void rechazaTextosDemasiadoLargos() {
        DataImportValidator corto = conLimites(1_000, 10, 10, 10, 10, 5, 10, 20);

        rechaza(corto, conProyecto("\"name\":\"" + "x".repeat(11) + "\",\"category\":\"TRABAJO\""),
                "projects[0].name");
        rechaza(corto, conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\""
                + "x".repeat(11) + "\"}]"), "projects[0].tasks[0].title");
        rechaza(corto, conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"description\":\""
                + "x".repeat(21) + "\""), "projects[0].description");
        rechaza(corto, conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"T\","
                + "\"workLogs\":[" + registro("2026-11-20", "1", "\"note\":\"" + "x".repeat(21) + "\"") + "]}]"),
                "projects[0].tasks[0].workLogs[0].note");
    }

    @Test
    void rechazaUnaReferenciaNoValidaOUnaRepetida() {
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"ref\":\"a\",\"title\":\"A\"},"
                + "{\"ref\":\"a\",\"title\":\"B\"}]"), "projects[0].tasks[1].ref");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"ref\":\"\",\"title\":\"A\"}]"),
                "projects[0].tasks[0].ref");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"ref\":\"" + "r".repeat(65)
                + "\",\"title\":\"A\"}]"), "projects[0].tasks[0].ref");
    }

    @Test
    void rechazaHorasFueraDeRangoOConDemasiadosDecimales() {
        rechaza(tareaConRegistro(registro("2026-11-20", "0")), "workLogs[0].hours");
        rechaza(tareaConRegistro(registro("2026-11-20", "25")), "workLogs[0].hours");
        rechaza(tareaConRegistro(registro("2026-11-20", "1.234")), "workLogs[0].hours");
        assertThatCode(() -> aceptar(validator, tareaConRegistro(registro("2026-11-20", "24"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> aceptar(validator, tareaConRegistro(registro("2026-11-20", "0.1"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaUnRegistroSinFechaTipoOHoras() {
        rechaza(tareaConRegistro("{\"activityType\":\"TEST\",\"hours\":1}"), "workLogs[0].date");
        rechaza(tareaConRegistro("{\"date\":\"2026-11-20\",\"hours\":1}"), "workLogs[0].activityType");
        rechaza(tareaConRegistro("{\"date\":\"2026-11-20\",\"activityType\":\"TEST\"}"), "workLogs[0].hours");
    }

    @Test
    void rechazaUnaDuracionNegativaONula() {
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"estimatedDuration\":-1"),
                "projects[0].estimatedDuration");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"A\","
                + "\"estimatedDuration\":0}]"), "projects[0].tasks[0].estimatedDuration");
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"estimatedDuration\":10000"),
                "projects[0].estimatedDuration");
    }

    @Test
    void rechazaFechasFueraDeRango() {
        rechaza(conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"A\","
                + "\"dueDate\":\"+10000-01-01\"}]"), "projects[0].tasks[0].dueDate");
        rechaza(tareaConRegistro(registro("+10000-01-01", "1")), "workLogs[0]");
    }

    // -------------------------------------------------------------------------
    // Límites
    // -------------------------------------------------------------------------

    @Test
    void rechazaDemasiadosProyectosTareasRegistrosYDependencias() {
        DataImportValidator pequeno = conLimites(1_000, 2, 3, 2, 1, 5, 500, 10_000);

        rechaza(pequeno, fichero("[{\"name\":\"A\",\"category\":\"TRABAJO\"},{\"name\":\"B\",\"category\":\"TRABAJO\"},"
                + "{\"name\":\"C\",\"category\":\"TRABAJO\"}]", null, null), "projects");
        rechaza(pequeno, conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"1\"},"
                + "{\"title\":\"2\",\"subtasks\":[{\"title\":\"3\"},{\"title\":\"4\"}]}]"), "tasks");
        rechaza(pequeno, tareaConRegistro(registro("2026-11-20", "1") + "," + registro("2026-11-21", "1")
                + "," + registro("2026-11-22", "1")), "registros de trabajo");
        rechaza(pequeno, fichero("[{\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"ref\":\"a\",\"title\":\"A\"},"
                + "{\"ref\":\"b\",\"title\":\"B\"}]}]", null,
                "[{\"task\":\"a\",\"dependsOn\":\"b\"},{\"task\":\"b\",\"dependsOn\":\"a\"}]"), "dependencies");
    }

    @Test
    void rechazaMasNivelesDeSubtareasQueElMaximo() {
        DataImportValidator corto = conLimites(1_000, 10, 100, 10, 10, 2, 500, 10_000);

        // Raíz (nivel 0) + 2 niveles de subtareas: dentro del máximo
        assertThatCode(() -> aceptar(corto, anidada(2))).doesNotThrowAnyException();
        // Un nivel más: fuera
        rechaza(corto, anidada(3), "niveles de subtareas");
    }

    @Test
    void rechazaUnFicheroConDemasiadosObjetosSinMapearlo() {
        DataImportValidator corto = conLimites(5, 10, 10, 10, 10, 5, 500, 10_000);

        rechaza(corto, "{\"a\":[{},{},{},{},{},{}]}", "demasiados elementos");
    }

    @Test
    void rechazaUnFicheroDemasiadoAnidado() {
        DataImportValidator corto = conLimites(1_000, 10, 10, 10, 10, 1, 500, 10_000);

        rechaza(corto, "[".repeat(40) + "]".repeat(40), "demasiado anidado");
    }

    // -------------------------------------------------------------------------
    // Dependencias
    // -------------------------------------------------------------------------

    @Test
    void rechazaUnaDependenciaAUnaReferenciaInexistente() {
        String proyectos = "[{\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"ref\":\"a\",\"title\":\"A\"}]}]";

        rechaza(fichero(proyectos, null, "[{\"task\":\"a\",\"dependsOn\":\"zzz\"}]"), "dependencies[0].dependsOn");
        rechaza(fichero(proyectos, null, "[{\"task\":\"zzz\",\"dependsOn\":\"a\"}]"), "dependencies[0].task");
        rechaza(fichero(proyectos, null, "[{\"task\":\"a\"}]"), "dependencies[0].dependsOn");
        rechaza(fichero(proyectos, null, "[null]"), "dependencies[0]");
    }

    @Test
    void rechazaUnaAutodependencia() {
        String proyectos = "[{\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"ref\":\"a\",\"title\":\"A\"}]}]";

        rechaza(fichero(proyectos, null, "[{\"task\":\"a\",\"dependsOn\":\"a\"}]"), "no puede depender de sí misma");
    }

    @Test
    void rechazaUnCicloDeDependencias() {
        String proyectos = "[{\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"ref\":\"a\",\"title\":\"A\"},"
                + "{\"ref\":\"b\",\"title\":\"B\"},{\"ref\":\"c\",\"title\":\"C\"}]}]";

        rechaza(fichero(proyectos, null,
                "[{\"task\":\"a\",\"dependsOn\":\"b\"},{\"task\":\"b\",\"dependsOn\":\"c\"},"
                        + "{\"task\":\"c\",\"dependsOn\":\"a\"}]"), "ciclo");
        // Sin ciclo: un rombo es válido
        assertThatCode(() -> aceptar(validator, fichero(proyectos, null,
                "[{\"task\":\"a\",\"dependsOn\":\"b\"},{\"task\":\"a\",\"dependsOn\":\"c\"},"
                        + "{\"task\":\"b\",\"dependsOn\":\"c\"}]"))).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private void rechaza(String json, String fragmento) {
        rechaza(validator, json, fragmento);
    }

    private static void rechaza(DataImportValidator validador, String json, String fragmento) {
        assertThatThrownBy(() -> aceptar(validador, json))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(fragmento);
    }

    /** Lee y valida un fichero: lanza {@link BusinessException} si no es aceptable. */
    private static void aceptar(DataImportValidator validador, String json) {
        validador.validate(validador.parse(json.getBytes()));
    }

    private static DataImportValidator conLimites(int objetos, int proyectos, int tareas, int registros,
                                                  int dependencias, int profundidad, int nombre, int texto) {
        return new DataImportValidator(MAPPER, objetos, proyectos, tareas, registros, dependencias,
                profundidad, nombre, texto);
    }

    /** Fichero válido con un proyecto cuyos campos se indican a mano. */
    private static String conProyecto(String camposDelProyecto) {
        return fichero("[{" + camposDelProyecto + "}]", null, null);
    }

    private static String fichero(String proyectos, String tareas, String dependencias) {
        StringBuilder json = new StringBuilder("{\"format\":\"taskmaster-export\",\"version\":1");
        if (proyectos != null) {
            json.append(",\"projects\":").append(proyectos);
        }
        if (tareas != null) {
            json.append(",\"tasks\":").append(tareas);
        }
        if (dependencias != null) {
            json.append(",\"dependencies\":").append(dependencias);
        }
        return json.append('}').toString();
    }

    private static String tareaConRegistro(String registros) {
        return conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[{\"title\":\"T\",\"workLogs\":["
                + registros + "]}]");
    }

    private static String registro(String fecha, String horas, String... extra) {
        String resto = extra.length == 0 ? "" : "," + String.join(",", extra);
        return "{\"date\":\"" + fecha + "\",\"activityType\":\"TEST\",\"hours\":" + horas + resto + "}";
    }

    /** Una raíz con {@code niveles} niveles de subtareas encadenadas. */
    private static String anidada(int niveles) {
        String tarea = "{\"title\":\"nivel " + niveles + "\"}";
        for (int nivel = niveles - 1; nivel >= 0; nivel--) {
            tarea = "{\"title\":\"nivel " + nivel + "\",\"subtasks\":[" + tarea + "]}";
        }
        return conProyecto("\"name\":\"P\",\"category\":\"TRABAJO\",\"tasks\":[" + tarea + "]");
    }
}
