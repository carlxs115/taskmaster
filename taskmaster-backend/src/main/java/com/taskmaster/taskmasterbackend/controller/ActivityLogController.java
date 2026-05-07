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
 * solo puede consultar su propio historial, ya que el {@code userId}
 * se extrae siempre del token de autenticación, nunca del request.</p>
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
     * @return 200 OK con la lista de registros de actividad
     */
    @GetMapping("/activity-log")
    public ResponseEntity<List<ActivityLogDTO>> getActivityLog(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        List<ActivityLogDTO> entries = activityLogService
                .getActivityHistory(userId)
                .stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/access-log
     * Devuelve el historial de accesos del usuario autenticado
     * (LOGIN, LOGOUT y PASSWORD_CHANGED).
     * Se usa en la sección de seguridad del perfil.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con la lista de registros de acceso
     */
    @GetMapping("/access-log")
    public ResponseEntity<List<ActivityLogDTO>> getAccessLog(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        List<ActivityLogDTO> entries = activityLogService
                .getAccessHistory(userId)
                .stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/activity-log/entity?entityType=TASK&entityId=5
     * Devuelve el historial de actividad de una entidad concreta.
     * El userId se extrae del token para garantizar que solo se devuelven
     * registros del usuario autenticado.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @param entityType  tipo de entidad ({@code "TASK"}, {@code "PROJECT"}, etc.)
     * @param entityId    identificador de la entidad
     * @return 200 OK con la lista de registros de actividad de esa entidad
     */
    @GetMapping("/activity-log/entity")
    public ResponseEntity<List<ActivityLogDTO>> getEntityActivityLog(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        Long userId = securityUtils.getUserId(userDetails);
        List<ActivityLogDTO> entries = activityLogService
                .getEntityHistory(userId, entityType, entityId)
                .stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/activity-log/entities?entityType=TASK&entityIds=1,2,3
     * Devuelve el historial de actividad para una lista de entidades del mismo tipo.
     * Si {@code entityIds} está vacía, el servicio devuelve una lista vacía sin consultar BD.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @param entityType  tipo de entidad
     * @param entityIds   lista de identificadores separados por coma
     * @return 200 OK con la lista de registros de actividad
     */
    @GetMapping("/activity-log/entities")
    public ResponseEntity<List<ActivityLogDTO>> getEntitiesActivityLog(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String entityType,
            @RequestParam List<Long> entityIds) {
        Long userId = securityUtils.getUserId(userDetails);
        List<ActivityLogDTO> entries = activityLogService
                .getHistoryForEntities(userId, entityType, entityIds)
                .stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(entries);
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Convierte una entidad {@link ActivityLog} a su DTO para la respuesta JSON.
     * El DTO omite la referencia al usuario para no exponer datos innecesarios.
     *
     * @param activityLog entidad a convertir
     * @return DTO con los datos del registro
     */
    private ActivityLogDTO toDTO(ActivityLog activityLog) {
        return ActivityLogDTO.builder()
                .id(activityLog.getId())
                .actionType(activityLog.getActionType())
                .entityType(activityLog.getEntityType())
                .entityId(activityLog.getEntityId())
                .entityName(activityLog.getEntityName())
                .oldValue(activityLog.getOldValue())
                .newValue(activityLog.getNewValue())
                .createdAt(activityLog.getCreatedAt())
                .build();
    }
}
