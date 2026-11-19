package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.response.BackupStatusResponse;
import com.taskmaster.taskmasterbackend.dto.response.RestoreStagedResponse;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

/**
 * Controlador REST de la copia de seguridad y la restauración.
 *
 * <p>Todos los endpoints requieren autenticación (regla general de
 * {@link com.taskmaster.taskmasterbackend.security.SecurityConfig}) y solo funcionan cuando
 * la base de datos tiene una única cuenta. Los errores los maneja
 * {@link com.taskmaster.taskmasterbackend.exception.GlobalExceptionHandler}: 400 para las
 * reglas de negocio y 413 si la copia es demasiado grande.</p>
 *
 * <p>El cliente nunca manda rutas: la copia viaja en el cuerpo de la respuesta o de la
 * petición, así que no hay forma de escribir en una ruta arbitraria.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    /** Tipo MIME de la copia de seguridad. */
    private static final String APPLICATION_ZIP = "application/zip";

    private final BackupService backupService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/backup/status
     * Indica si la copia de seguridad está disponible y si hay una restauración pendiente.
     *
     * @return 200 OK con {@code available} y {@code restorePending}
     */
    @GetMapping("/status")
    public ResponseEntity<BackupStatusResponse> getStatus() {
        return ResponseEntity.ok(backupService.getStatus());
    }

    /**
     * GET /api/backup
     * Descarga una copia de seguridad recién creada.
     *
     * <p>El zip temporal se abre con {@code DELETE_ON_CLOSE}: se borra en cuanto Spring
     * termina de enviarlo y cierra el flujo. Los restos que queden los borra el arranque.</p>
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el zip como adjunto, o 400 si la copia no está disponible
     */
    @GetMapping
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        Path zip = backupService.createBackup(userId);
        try {
            long size = Files.size(zip);
            InputStream content = Files.newInputStream(zip, StandardOpenOption.DELETE_ON_CLOSE);
            String filename = "taskmaster-copia-" + LocalDate.now() + ".zip";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(APPLICATION_ZIP))
                    .contentLength(size)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .body(new InputStreamResource(content));
        } catch (IOException e) {
            try {
                Files.deleteIfExists(zip);
            } catch (IOException ignored) {
                // Lo borrará el arranque
            }
            throw new UncheckedIOException("No se pudo enviar la copia de seguridad", e);
        }
    }

    /**
     * POST /api/backup/restore?password=…
     * Valida una copia de seguridad y la deja preparada para aplicarse en el siguiente arranque.
     *
     * <p>El cuerpo es el zip en bruto ({@code application/zip}), no multipart: el límite
     * global de multipart está pensado para avatares.</p>
     *
     * @param password    contraseña actual para confirmar la operación
     * @param body        contenido del zip
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 202 Accepted con los usuarios de la copia; 400 si la contraseña, las cuentas
     *         o el fichero no son válidos; 413 si es demasiado grande
     */
    @PostMapping(value = "/restore", consumes = APPLICATION_ZIP)
    public ResponseEntity<RestoreStagedResponse> restore(
            @RequestParam String password,
            InputStream body,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = securityUtils.getUserId(userDetails);
        RestoreStagedResponse response = backupService.stageRestore(userId, password, body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * DELETE /api/backup/restore
     * Cancela la restauración pendiente.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 204 No Content, o 400 si la copia no está disponible
     */
    @DeleteMapping("/restore")
    public ResponseEntity<Void> cancelRestore(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        backupService.cancelPendingRestore(userId);
        return ResponseEntity.noContent().build();
    }
}
