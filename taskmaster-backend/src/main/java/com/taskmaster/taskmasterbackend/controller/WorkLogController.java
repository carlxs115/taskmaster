package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.WorkLogDTO;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.WorkLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controlador REST que gestiona los registros de trabajo asociados a las tareas.
 *
 * <p>Permite crear, actualizar, eliminar y consultar registros de trabajo,
 * así como obtener el total de horas invertidas en una tarea incluyendo
 * sus subtareas.</p>
 *
 * <p><b>Seguridad:</b> las operaciones de escritura (crear, actualizar, eliminar)
 * validan que el worklog pertenece al usuario autenticado antes de ejecutarse.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/worklogs")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogService workLogService;
    private final SecurityUtils securityUtils;

    /**
     * POST /api/worklogs/task/{taskId}
     * Crea un nuevo registro de trabajo asociado a una tarea.
     *
     * @param taskId      identificador de la tarea
     * @param dto         datos del registro validados con {@code @Valid}
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 201 Created con el registro creado
     */
    @PostMapping("/task/{taskId}")
    public ResponseEntity<WorkLogDTO> create(
            @PathVariable Long taskId,
            @Valid @RequestBody WorkLogDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workLogService.create(taskId, dto, userId));
    }

    /**
     * PUT /api/worklogs/{id}
     * Actualiza un registro de trabajo existente.
     * Valida que el registro pertenece al usuario autenticado.
     *
     * @param id          identificador del registro
     * @param dto         nuevos datos del registro validados con {@code @Valid}
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el registro actualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<WorkLogDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody WorkLogDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(workLogService.update(id, dto, userId));
    }

    /**
     * DELETE /api/worklogs/{id}
     * Elimina un registro de trabajo.
     * Valida que el registro pertenece al usuario autenticado.
     *
     * @param id          identificador del registro a eliminar
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        workLogService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/worklogs/task/{taskId}
     * Devuelve todos los registros de trabajo de una tarea.
     *
     * @param taskId identificador de la tarea
     * @return 200 OK con la lista de registros de trabajo
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<WorkLogDTO>> getByTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(workLogService.getByTask(taskId));
    }

    /**
     * GET /api/worklogs/task/{taskId}/total
     * Devuelve el total de horas registradas para una tarea,
     * incluyendo las horas de todas sus subtareas de forma recursiva.
     *
     * @param taskId identificador de la tarea raíz
     * @return 200 OK con el total de horas como {@link BigDecimal}
     */
    @GetMapping("/task/{taskId}/total")
    public ResponseEntity<BigDecimal> getTotalHours(@PathVariable Long taskId) {
        return ResponseEntity.ok(workLogService.getTotalHours(taskId));
    }
}
