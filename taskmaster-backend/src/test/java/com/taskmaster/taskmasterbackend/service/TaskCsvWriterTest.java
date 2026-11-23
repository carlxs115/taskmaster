package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.dto.transfer.DependencyData;
import com.taskmaster.taskmasterbackend.dto.transfer.ProjectData;
import com.taskmaster.taskmasterbackend.dto.transfer.TaskData;
import com.taskmaster.taskmasterbackend.dto.transfer.WorkLogData;
import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link TaskCsvWriter}: formato por idioma, escapado y protección frente a
 * inyección de fórmulas. No usa Spring ni base de datos.
 *
 * @author Carlos
 */
class TaskCsvWriterTest {

    private final TaskCsvWriter writer = new TaskCsvWriter();

    @Test
    void escribeBomCrlfYCabecerasEnEspanol() {
        byte[] csv = writer.write(exportacion(personal("Comprar pan")), true);

        assertThat(csv).startsWith(0xEF, 0xBB, 0xBF);
        String texto = new String(csv, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿Referencia;Tarea padre;Proyecto;Título;Descripción;Estado;Prioridad;"
                + "Categoría;Fecha límite;Duración estimada (h);Horas registradas;Depende de;Creada\r\n");
        assertThat(texto).endsWith("\r\n");
        // Todos los saltos de línea son CRLF
        assertThat(texto.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void escribeCabecerasEnInglesConComa() {
        String texto = new String(writer.write(exportacion(personal("Buy bread")), false), StandardCharsets.UTF_8);

        assertThat(texto).startsWith("﻿Ref,Parent,Project,Title,Description,Status,Priority,Category,"
                + "Due date,Estimated hours,Logged hours,Depends on,Created\r\n");
    }

    @Test
    void usaComaDecimalEnEspanolYPuntoEnIngles() {
        TaskData tarea = tarea("t1", "Con horas", null, new BigDecimal("1.50"),
                List.of(registro("2.50"), registro("1.25")), List.of());
        DataExportFile file = exportacion(tarea);

        String es = new String(writer.write(file, true), StandardCharsets.UTF_8);
        String en = new String(writer.write(file, false), StandardCharsets.UTF_8);

        assertThat(es).contains(";1,50;3,75;");
        assertThat(en).contains(",1.50,3.75,");
    }

    @Test
    void sumaLasHorasRegistradasYListaLasDependencias() {
        TaskData a = tarea("t1", "A", null, null, List.of(registro("2"), registro("0.5")), List.of());
        TaskData b = tarea("t2", "B", null, null, List.of(), List.of());
        TaskData c = tarea("t3", "C", null, null, List.of(), List.of());
        DataExportFile file = new DataExportFile(DataExportFile.FORMAT, DataExportFile.VERSION, null, List.of(),
                List.of(a, b, c), List.of(new DependencyData("t1", "t2"), new DependencyData("t1", "t3")));

        List<String> filas = filas(writer.write(file, false));

        assertThat(filas.get(1)).contains(",2.5,t2 t3,");
        assertThat(filas.get(2)).contains(",0,,2026-10-02 10:00");
    }

    @Test
    void incluyeProyectoYTareaPadreEnLasSubtareas() {
        TaskData subtarea = tarea("t2", "Hija", null, null, List.of(), List.of());
        TaskData raiz = tarea("t1", "Madre", null, null, List.of(), List.of(subtarea));
        ProjectData proyecto = new ProjectData("TFC", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                TaskCategory.ESTUDIOS, null, null, List.of(raiz));
        DataExportFile file = new DataExportFile(DataExportFile.FORMAT, DataExportFile.VERSION, null,
                List.of(proyecto), List.of(), List.of());

        List<String> filas = filas(writer.write(file, false));

        assertThat(filas).hasSize(3);
        assertThat(filas.get(1)).startsWith("t1,,TFC,Madre,");
        assertThat(filas.get(2)).startsWith("t2,t1,TFC,Hija,");
    }

    @Test
    void entrecomillaLosCamposConSeparadorComillasSaltosOEspaciosAlBorde() {
        assertThat(TaskCsvWriter.escape("a;b", ';')).isEqualTo("\"a;b\"");
        assertThat(TaskCsvWriter.escape("a,b", ';')).isEqualTo("a,b");
        assertThat(TaskCsvWriter.escape("di \"hola\"", ';')).isEqualTo("\"di \"\"hola\"\"\"");
        assertThat(TaskCsvWriter.escape("uno\ndos", ';')).isEqualTo("\"uno\ndos\"");
        assertThat(TaskCsvWriter.escape("uno\r\ndos", ';')).isEqualTo("\"uno\r\ndos\"");
        assertThat(TaskCsvWriter.escape(" borde", ';')).isEqualTo("\" borde\"");
        assertThat(TaskCsvWriter.escape("borde ", ';')).isEqualTo("\"borde \"");
        assertThat(TaskCsvWriter.escape("normal", ';')).isEqualTo("normal");
        assertThat(TaskCsvWriter.escape("", ';')).isEmpty();
    }

    @Test
    void neutralizaLosTitulosQueEmpiezanComoUnaFormula() {
        for (String titulo : List.of("=1+1", "+1", "-1", "@SUM(A1)", "\t=1", "\r=1")) {
            assertThat(TaskCsvWriter.userText(titulo)).as(titulo).startsWith("'").isEqualTo("'" + titulo);
        }
        assertThat(TaskCsvWriter.userText("normal")).isEqualTo("normal");
        assertThat(TaskCsvWriter.userText("a=b")).isEqualTo("a=b");
        assertThat(TaskCsvWriter.userText(null)).isEmpty();
    }

    @Test
    void elApostrofoSePoneAntesDelEscapado() {
        String texto = new String(writer.write(exportacion(personal("=A1;B1")), true), StandardCharsets.UTF_8);

        assertThat(texto).contains(";\"'=A1;B1\";");
    }

    @Test
    void neutralizaTambienElProyectoYLaDescripcion() {
        TaskData tarea = tarea("t1", "T", "=cmd|' /C calc'!A0", null, List.of(), List.of());
        ProjectData proyecto = new ProjectData("@proyecto", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                TaskCategory.TRABAJO, null, null, List.of(tarea));
        DataExportFile file = new DataExportFile(DataExportFile.FORMAT, DataExportFile.VERSION, null,
                List.of(proyecto), List.of(), List.of());

        String fila = filas(writer.write(file, false)).get(1);

        assertThat(fila).contains(",'@proyecto,").contains(",'=cmd|' /C calc'!A0,");
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    /** Filas del CSV sin el BOM, partidas por CRLF (los tests no usan saltos dentro de un campo). */
    private static List<String> filas(byte[] csv) {
        String texto = new String(csv, StandardCharsets.UTF_8).substring(1);
        return List.of(texto.split("\r\n"));
    }

    private static DataExportFile exportacion(TaskData personal) {
        return new DataExportFile(DataExportFile.FORMAT, DataExportFile.VERSION, null, List.of(),
                List.of(personal), List.of());
    }

    private static TaskData personal(String titulo) {
        return tarea("t1", titulo, null, null, List.of(), List.of());
    }

    private static TaskData tarea(String ref, String titulo, String descripcion, BigDecimal duracion,
                                  List<WorkLogData> registros, List<TaskData> subtareas) {
        return new TaskData(ref, titulo, descripcion, TaskStatus.TODO, TaskPriority.MEDIUM,
                TaskCategory.PERSONAL, LocalDate.of(2026, 12, 15), duracion,
                LocalDateTime.of(2026, 10, 2, 10, 0), registros, subtareas);
    }

    private static WorkLogData registro(String horas) {
        return new WorkLogData(LocalDate.of(2026, 11, 20), ActivityType.TEST, new BigDecimal(horas), null);
    }
}
