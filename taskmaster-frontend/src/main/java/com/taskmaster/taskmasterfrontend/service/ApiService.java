package com.taskmaster.taskmasterfrontend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
 * @author Carlos
 */
public class ApiService {

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
     * en las siguientes peticiones. Se llama desde {@link com.taskmaster.taskmasterfrontend.controller.LoginController}
     * tras un login exitoso.
     *
     * @param username Nombre de usuario.
     * @param password Contraseña en texto plano.
     */
    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Genera la cabecera de autenticación HTTP Basic.
     *
     * @return Cadena con el formato {@code "Basic <base64(usuario:contraseña)>"}.
     */
    private String getAuthHeader() {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Envía una petición POST sin autenticación, usada para los endpoints
     * públicos de login y registro.
     *
     * @param endpoint Ruta del endpoint (p.ej. {@code "/api/auth/login"}).
     * @param body     Objeto Java que se serializará a JSON como cuerpo.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red o de serialización.
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

    /**
     * Envía una petición POST con autenticación y cuerpo JSON.
     *
     * @param endpoint Ruta del endpoint.
     * @param body     Objeto Java que se serializará a JSON como cuerpo.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red o de serialización.
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
     * Envía una petición POST con autenticación y sin cuerpo,
     * pasando los parámetros directamente en la URL.
     *
     * @param endpoint Ruta del endpoint con los parámetros ya codificados.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red.
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
     * @param endpoint Ruta del endpoint.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red.
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
     * @param endpoint Ruta del endpoint.
     * @param body     Objeto Java que se serializará a JSON como cuerpo.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red o de serialización.
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
     * Envía una petición DELETE con autenticación.
     *
     * @param endpoint Ruta del endpoint.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red.
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
     * Envía una petición PATCH con autenticación. Acepta un cuerpo JSON,
     * una cadena ya serializada o {@code null} para peticiones sin cuerpo.
     *
     * @param endpoint Ruta del endpoint.
     * @param body     Objeto a serializar, cadena JSON ya preparada, o {@code null}.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red o de serialización.
     */
    public HttpResponse<String> patch(String endpoint, Object body) throws Exception {
        String json;
        if (body == null) {
            json = "";
        } else if (body instanceof String s) {
            json = s; // ya es JSON, no serializar de nuevo
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
     * Envía una petición PUT con autenticación y sin cuerpo,
     * pasando los parámetros directamente en la URL.
     *
     * @param endpoint Ruta del endpoint con los parámetros ya codificados.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red.
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
     * Envía una petición GET con autenticación y devuelve el cuerpo como array de bytes,
     * útil para descargar recursos binarios como imágenes de avatar.
     *
     * @param endpoint Ruta del endpoint.
     * @return Array de bytes con el contenido de la respuesta,
     *         o {@code null} si el servidor responde con 404.
     * @throws Exception Si el servidor responde con un error distinto de 404,
     *                   o si se produce un error de red.
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
            throw new RuntimeException("Error HTTP " + response.statusCode() + " en " + endpoint);
        }
        return response.body();
    }

    /**
     * Envía una petición POST con autenticación y un archivo como cuerpo
     * {@code multipart/form-data}, construyendo el límite y las cabeceras
     * de la parte manualmente.
     *
     * @param endpoint    Ruta del endpoint.
     * @param fieldName   Nombre del campo del formulario (p.ej. {@code "file"}).
     * @param filename    Nombre del archivo anunciado al servidor.
     * @param contentType Tipo MIME del archivo (p.ej. {@code "image/png"}).
     * @param fileBytes   Contenido binario del archivo.
     * @return Respuesta HTTP del servidor.
     * @throws Exception Si se produce un error de red o de escritura del cuerpo.
     */
    public HttpResponse<String> postMultipart(String endpoint, String fieldName,
                                              String filename, String contentType,
                                              byte[] fileBytes) throws Exception {
        String boundary = "----TaskMasterBoundary" + System.currentTimeMillis();
        String CRLF = "\r\n";

        // Construcción manual del cuerpo multipart
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        String preamble = "--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"" + CRLF
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
     * @param response Respuesta HTTP cuyo cuerpo se deserializará.
     * @param clazz    Clase destino de la deserialización.
     * @param <T>      Tipo del objeto resultante.
     * @return Objeto Java deserializado.
     * @throws Exception Si el JSON no puede deserializarse a la clase indicada.
     */
    public <T> T parseResponse(HttpResponse<String> response, Class<T> clazz) throws Exception {
        return objectMapper.readValue(response.body(), clazz);
    }
}
