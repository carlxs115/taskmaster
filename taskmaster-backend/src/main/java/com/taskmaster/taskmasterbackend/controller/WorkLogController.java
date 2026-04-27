package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.WorkLogDTO;
import com.taskmaster.taskmasterbackend.service.WorkLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controlador REST que gestiona los registros de trabajo asociados a las tareas.
 *
 * <p>Permite crear, actualizar, eliminar y consultar registros de trabajo,
 * así como obtener el total de horas invertidas en una tarea.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/worklogs")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogService workLogService;

    /**
     * POST /api/worklogs/task/{taskId}
     * Crea un nuevo registro de trabajo asociado a una tarea.
     *
     * @param taskId identificador de la tarea
     * @param dto    datos del registro validados
     * @return registro creado con código 201 Created
     */
    @PostMapping("/task/{taskId}")
    public ResponseEntity<WorkLogDTO> create(
            @PathVariable Long taskId,
            @Valid @RequestBody WorkLogDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workLogService.create(taskId, dto));
    }

    /**
     * PUT /api/worklogs/{id}
     * Actualiza un registro de trabajo existente.
     *
     * @param id  identificador del registro
     * @param dto nuevos datos del registro validados
     * @return registro actualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<WorkLogDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody WorkLogDTO dto) {
        return ResponseEntity.ok(workLogService.update(id, dto));
    }

    /**
     * DELETE /api/worklogs/{id}
     * Elimina un registro de trabajo.
     *
     * @param id identificador del registro a eliminar
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workLogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/worklogs/task/{taskId}
     * Devuelve todos los registros de trabajo de una tarea.
     *
     * @param taskId identificador de la tarea
     * @return lista de registros de trabajo
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<WorkLogDTO>> getByTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(workLogService.getByTask(taskId));
    }

    /**
     * GET /api/worklogs/task/{taskId}/total
     * Devuelve el total de horas registradas para una tarea, incluyendo sus subtareas.
     *
     * @param taskId identificador de la tarea
     * @return total de horas como {@link BigDecimal}
     */
    @GetMapping("/task/{taskId}/total")
    public ResponseEntity<BigDecimal> getTotalHours(@PathVariable Long taskId) {
        return ResponseEntity.ok(workLogService.getTotalHours(taskId));
    }
}
