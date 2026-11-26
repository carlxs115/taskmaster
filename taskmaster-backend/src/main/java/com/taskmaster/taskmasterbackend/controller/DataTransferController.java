package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.response.ImportResultResponse;
import com.taskmaster.taskmasterbackend.security.SecurityUtils;
import com.taskmaster.taskmasterbackend.service.DataTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.time.LocalDate;

/**
 * Controlador REST de la exportación e importación de datos de la cuenta.
 *
 * <p>Todos los endpoints requieren autenticación (regla general de
 * {@link com.taskmaster.taskmasterbackend.security.SecurityConfig}) y trabajan solo con los
 * datos del usuario autenticado. Los errores los maneja
 * {@link com.taskmaster.taskmasterbackend.exception.GlobalExceptionHandler}: 400 para un
 * fichero no válido y 413 si es demasiado grande.</p>
 *
 * @author Carlos
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataTransferController {

    /** Tipo MIME del CSV exportado. */
    private static final String TEXT_CSV = "text/csv;charset=UTF-8";

    private final DataTransferService dataTransferService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/data/export/json
     * Descarga los proyectos, tareas, subtareas, registros de trabajo y dependencias
     * activos del usuario en JSON.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el JSON como adjunto
     */
    @GetMapping("/export/json")
    public ResponseEntity<byte[]> exportJson(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return attachment(dataTransferService.exportJson(userId), MediaType.APPLICATION_JSON_VALUE,
                "taskmaster-datos-" + LocalDate.now() + ".json");
    }

    /**
     * GET /api/data/export/csv?lang=es|en
     * Descarga las tareas activas del usuario en CSV, una fila por tarea o subtarea.
     *
     * @param lang        idioma de las cabeceras y del formato ({@code es} por defecto;
     *                    cualquier valor distinto de {@code en} se trata como español)
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con el CSV como adjunto
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "es") String lang,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        boolean spanish = !"en".equalsIgnoreCase(lang);
        return attachment(dataTransferService.exportCsv(userId, spanish), TEXT_CSV,
                "taskmaster-tareas-" + LocalDate.now() + ".csv");
    }

    /**
     * POST /api/data/import
     * Importa de forma aditiva un fichero JSON exportado por TaskMaster.
     *
     * <p>El cuerpo es el JSON en bruto ({@code application/json}) y se lee como flujo con tope
     * de tamaño: con {@code @RequestBody}, Spring lo leería entero sin límite de tamaño ni de
     * anidamiento antes de llegar al servicio.</p>
     *
     * @param body        contenido del fichero
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return 200 OK con los elementos creados; 400 si el fichero no es válido; 413 si es
     *         demasiado grande
     */
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImportResultResponse> importData(InputStream body,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = securityUtils.getUserId(userDetails);
        return ResponseEntity.ok(dataTransferService.importJson(userId, body));
    }

    /** Respuesta 200 con el contenido como adjunto descargable. */
    private static ResponseEntity<byte[]> attachment(byte[] content, String contentType, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
