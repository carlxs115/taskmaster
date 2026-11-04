package com.taskmaster.taskmasterbackend.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de {@link GlobalExceptionHandler}: cada tipo de error sale con su código HTTP
 * (fallo interno → 500; error del cliente → 4xx) y sin exponer detalles internos.
 *
 * @author Carlos
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockMvc mockMvc;

    /** Controlador mínimo que provoca cada tipo de error. */
    @RestController
    static class ControladorDePrueba {

        @GetMapping("/runtime")
        String runtime() {
            throw new RuntimeException("detalle interno SQL");
        }

        @PostMapping("/param")
        String param(@RequestParam String name) {
            return name;
        }

        @GetMapping("/tipo")
        Long tipo(@RequestParam Long id) {
            return id;
        }

        @PostMapping("/json")
        Map<String, Object> json(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/negocio")
        String negocio() {
            throw new BusinessException("regla violada");
        }

        @GetMapping("/no-encontrado")
        String noEncontrado() {
            throw new ResourceNotFoundException("Tarea no encontrada con id: 1");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void runtimeExceptionInesperadaDevuelve500SinDetallesInternos() throws Exception {
        mockMvc.perform(get("/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Error interno del servidor"))
                .andExpect(content().string(not(containsString("detalle interno"))));
    }

    @Test
    void faltaUnParametroObligatorioDevuelve400() throws Exception {
        mockMvc.perform(post("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Petición no válida"));
    }

    @Test
    void parametroConTipoIncorrectoDevuelve400() throws Exception {
        mockMvc.perform(get("/tipo").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Petición no válida"));
    }

    @Test
    void jsonMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Petición no válida"));
    }

    @Test
    void metodoNoPermitidoDevuelve405() throws Exception {
        mockMvc.perform(delete("/runtime"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void businessExceptionSigueDevolviendo400ConSuMensaje() throws Exception {
        mockMvc.perform(get("/negocio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("regla violada"));
    }

    @Test
    void resourceNotFoundSigueDevolviendo404ConSuMensaje() throws Exception {
        mockMvc.perform(get("/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Tarea no encontrada con id: 1"));
    }

    @Test
    void rutaInexistenteDevuelve404() {
        ResponseEntity<Map<String, Object>> respuesta =
                handler.handleGenericException(new NoResourceFoundException(HttpMethod.GET, "api/tasks/"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).containsEntry("error", "Recurso no encontrado");
    }

    @Test
    void ficheroDemasiadoGrandeDevuelve413() {
        ResponseEntity<Map<String, Object>> respuesta =
                handler.handleRuntimeException(new MaxUploadSizeExceededException(2048));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(413);
        assertThat(respuesta.getBody()).containsEntry("status", 413);
    }
}
