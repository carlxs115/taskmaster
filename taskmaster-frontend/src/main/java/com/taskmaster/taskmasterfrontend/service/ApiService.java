package com.taskmaster.taskmasterfrontend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * Servicio central de comunicación HTTP con el backend de TaskMaster.
 *
 * <p>Gestiona todas las peticiones REST utilizando el {@link HttpClient} nativo
 * de Java 11+ y autenticación HTTP Basic, codificando las credenciales en
 * Base64 en la cabecera {@code Authorization} de cada petición protegida.</p>
 *
 * <p><b>Nota de seguridad:</b> las credenciales se almacenan en memoria en
 * texto plano porque HTTP Basic las requiere en cada petición. Como mejora
 * futura se plantea migrar a autenticación JWT, eliminando esta necesidad.
 * Ver {@code AppContext} para más detalles.</p>
 *
 * <p><b>Configuración:</b> la URL base del backend está fijada a
 * {@code http://localhost:8080}, adecuada para uso en escritorio local.
 * Para entornos remotos habría que externalizarla a un fichero de configuración.</p>
 *
 * @author Carlos
 */
public class ApiService {

    private static final Logger log = LoggerFactory.getLogger(ApiService.class);

    /** URL base del backend. Fija para uso en escritorio local (localhost). */
    private static final String BASE_URL = "http://localhost:8080";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String username;
    private String password;

    /**
     * Inicializa el cliente HTTP y el mapeador de JSON con soporte
     * para tipos de fecha y hora de {@code java.time}.
     */
    public ApiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        // Necesario para serializar/deserializar LocalDate, LocalDateTime, etc.
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Almacena las credenciales del usuario autenticado para incluirlas
     * en las siguientes peticiones autenticadas.
     * Se llama desde {@code LoginController} tras un login exitoso,
     * y con {@code null, null} desde {@code AppContext.logout()}.
     *
     * @param username nombre de usuario, o {@code null} para limpiar credenciales
     * @param password contraseña en texto plano, o {@code null} para limpiar credenciales
     */
    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // -------------------------------------------------------------------------
    // Peticiones públicas (sin autenticación)
    // -------------------------------------------------------------------------

    /**
     * Envía una petición POST sin autenticación, usada para los endpoints
     * públicos de login y registro ({@code /api/auth/login}, {@code /api/auth/register}).
     *
     * @param endpoint ruta del endpoint (p.ej. {@code "/api/auth/login"})
     * @param body     objeto Java que se serializará a JSON como cuerpo
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red o de serialización
     */
    public HttpResponse<String> post(String endpoint, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // -------------------------------------------------------------------------
    // Peticiones autenticadas
    // -------------------------------------------------------------------------

    /**
     * Envía una petición POST con autenticación y cuerpo JSON.
     *
     * @param endpoint ruta del endpoint
     * @param body     objeto Java que se serializará a JSON como cuerpo
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red o de serialización
     */
    public HttpResponse<String> postWithAuth(String endpoint, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía una petición POST con autenticación y sin cuerpo.
     * Los parámetros se pasan directamente en la URL como query params.
     *
     * @param endpoint ruta del endpoint con los parámetros ya codificados
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red
     */
    public HttpResponse<String> postWithAuthNoBody(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthHeader())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía una petición GET con autenticación.
     *
     * @param endpoint ruta del endpoint
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red
     */
    public HttpResponse<String> get(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", getAuthHeader())
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía una petición PUT con autenticación y cuerpo JSON.
     *
     * @param endpoint ruta del endpoint
     * @param body     objeto Java que se serializará a JSON como cuerpo
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red o de serialización
     */
    public HttpResponse<String> put(String endpoint, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthHeader())
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía una petición PUT con autenticación y sin cuerpo.
     * Los parámetros se pasan directamente en la URL como query params.
     *
     * @param endpoint ruta del endpoint con los parámetros ya codificados
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red
     */
    public HttpResponse<String> putNoBody(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthHeader())
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía una petición PATCH con autenticación.
     *
     * <p>El parámetro {@code body} acepta tres casos:</p>
     * <ul>
     *   <li>{@code null} - petición sin cuerpo</li>
     *   <li>{@code String} - JSON ya serializado, se envía directamente sin re-serializar</li>
     *   <li>cualquier otro objeto - se serializa a JSON con Jackson</li>
     * </ul>
     *
     * @param endpoint ruta del endpoint
     * @param body     objeto a serializar, cadena JSON ya preparada, o {@code null}
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red o de serialización
     */
    public HttpResponse<String> patch(String endpoint, Object body) throws Exception {
        String json;
        if (body == null) {
            json = "";
        } else if (body instanceof String s) {
            // Si ya es un String lo enviamos directamente para evitar doble serialización
            json = s;
        } else {
            json = objectMapper.writeValueAsString(body);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthHeader())
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía una petición DELETE con autenticación.
     *
     * @param endpoint ruta del endpoint
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red
     */
    public HttpResponse<String> delete(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", getAuthHeader())
                .DELETE()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía una petición GET con autenticación y devuelve el cuerpo como array de bytes.
     * Útil para descargar recursos binarios como imágenes de avatar.
     *
     * @param endpoint ruta del endpoint
     * @return array de bytes con el contenido de la respuesta,
     *         o {@code null} si el servidor responde con 404
     * @throws Exception si el servidor responde con un código de error distinto de 404,
     *                   o si se produce un error de red
     */
    public byte[] getBytes(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", getAuthHeader())
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 404) return null;

        if (response.statusCode() != 200) {
            // SEGURIDAD: no incluimos el endpoint en el mensaje de error
            // porque puede contener IDs de usuario u otra información sensible
            log.error("Error HTTP {} al descargar recurso binario", response.statusCode());
            throw new RuntimeException("Error HTTP " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Envía una petición POST con autenticación y un archivo como cuerpo
     * {@code multipart/form-data}, construyendo el límite y las cabeceras
     * de la parte manualmente.
     *
     * @param endpoint    ruta del endpoint
     * @param fieldName   nombre del campo del formulario (p.ej. {@code "file"})
     * @param filename    nombre del archivo anunciado al servidor
     * @param contentType tipo MIME del archivo (p.ej. {@code "image/png"})
     * @param fileBytes   contenido binario del archivo
     * @return respuesta HTTP del servidor
     * @throws Exception si se produce un error de red o de escritura del cuerpo
     */
    public HttpResponse<String> postMultipart(String endpoint, String fieldName,
                                              String filename, String contentType,
                                              byte[] fileBytes) throws Exception {

        // Generamos un boundary único para esta petición
        String boundary = "----TaskMasterBoundary" + System.currentTimeMillis();
        String CRLF = "\r\n";

        // Construimos manualmente el cuerpo multipart:
        // --boundary
        // Content-Disposition y Content-Type de la parte
        // (línea vacía)
        // bytes del fichero
        // --boundary--
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String preamble = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"" + fieldName
                + "\"; filename=\"" + filename + "\"" + CRLF
                + "Content-Type: " + contentType + CRLF + CRLF;
        String epilogue = CRLF + "--" + boundary + "--" + CRLF;

        baos.write(preamble.getBytes());
        baos.write(fileBytes);
        baos.write(epilogue.getBytes());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", getAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Deserializa el cuerpo JSON de una respuesta HTTP a un objeto Java.
     *
     * @param response respuesta HTTP cuyo cuerpo se deserializará
     * @param clazz    clase destino de la deserialización
     * @param <T>      tipo del objeto resultante
     * @return objeto Java deserializado
     * @throws Exception si el JSON no puede deserializarse a la clase indicada
     */
    public <T> T parseResponse(HttpResponse<String> response, Class<T> clazz) throws Exception {
        return objectMapper.readValue(response.body(), clazz);
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Genera la cabecera de autenticación HTTP Basic codificando las credenciales
     * en Base64 con el formato {@code usuario:contraseña}.
     *
     * <p><b>Seguridad:</b> si las credenciales no están establecidas devuelve
     * una cabecera vacía en lugar de enviar {@code null:null} codificado,
     * lo que causaría un 401 limpio en lugar de un comportamiento inesperado.</p>
     *
     * @return cabecera con formato {@code "Basic <base64>"}, o cadena vacía
     *         si las credenciales no están establecidas
     */
    private String getAuthHeader() {
        // Si no hay credenciales devolvemos cadena vacía para obtener un 401 limpio
        // en lugar de enviar "null:null" codificado en Base64
        if (username == null || password == null) {
            log.warn("Se intentó hacer una petición autenticada sin credenciales establecidas");
            return "";
        }

        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}