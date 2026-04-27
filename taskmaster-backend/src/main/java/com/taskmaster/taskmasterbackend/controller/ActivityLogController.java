package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import com.taskmaster.taskmasterbackend.dto.ActivityLogDTO;
import com.taskmaster.taskmasterbackend.model.ActivityLog;
import com.taskmaster.taskmasterbackend.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST que expone los endpoints del historial de actividad.
 *
 * <p>Todos los endpoints requieren autenticación. El usuario autenticado
 * solo puede consultar su propio historial.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/activity-log
     * Devuelve el historial de actividad del usuario autenticado,
     * excluyendo los eventos de autenticación (login/logout).
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de registros de actividad
     */
    @GetMapping("/activity-log")
    public ResponseEntity<List<ActivityLogDTO>> getActivityLog(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        List<ActivityLogDTO> log = activityLogService
                .getActivityHistory(userId)
                .stream()
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(log);
    }

    /**
     * GET /api/access-log
     * Devuelve el historial de accesos (login y logout) del usuario autenticado.
     * Se usa en la sección de seguridad del perfil.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return lista de registros de acceso
     */
    @GetMapping("/access-log")
    public ResponseEntity<List<ActivityLogDTO>> getAccessLog(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        List<ActivityLogDTO> log = activityLogService
                .getAccessHistory(userId)
                .stream()
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(log);
    }

    /**
     * GET /api/activity-log/entity
     * Devuelve el historial de actividad de una entidad concreta.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @param entityType  tipo de entidad ({@code "TASK"}, {@code "PROJECT"}, etc.)
     * @param entityId    identificador de la entidad
     * @return lista de registros de actividad de esa entidad
     */
    @GetMapping("/activity-log/entity")
    public ResponseEntity<List<ActivityLogDTO>> getEntityActivityLog(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        Long userId = securityUtils.getUserId(userDetails);
        List<ActivityLogDTO> log = activityLogService
                .getEntityHistory(userId, entityType, entityId)
                .stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(log);
    }

    /**
     * Convierte una entidad {@link ActivityLog} a su DTO correspondiente.
     *
     * @param log entidad a convertir
     * @return DTO con los datos del registro
     */
    private ActivityLogDTO toDTO(ActivityLog log) {
        return ActivityLogDTO.builder()
                .id(log.getId())
                .actionType(log.getActionType())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .entityName(log.getEntityName())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
