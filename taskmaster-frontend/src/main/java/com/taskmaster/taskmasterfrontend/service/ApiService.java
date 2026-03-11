package com.taskmaster.taskmasterfrontend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * APISERVICE
 *
 * Clase central que gestiona todas las peticiones HTTP al backend.
 *
 * HttpClient -> cliente HTTP integrado en Java 11+, no necesita librerías externas
 * ObjectMapper -> convierte objetos Java a JSON y viceversa
 *
 * Usamos autenticación Basic Auth - en cada petición enviamos
 * username:password codificado en Base64 en la cabecera Authorization.
 * Es el mecanismo más simple compatible con Spring Security STATELESS.
 */
public class ApiService {

    private static final String BASE_URL = "http://localhost:8080";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String username;
    private String password;

    /**
     * Constructor - inicializa el cliente HTTP y el mapper de JSON.
     */
    public ApiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        // Necesario para serializar/deserializar LocalDate, LocalDateTime, etc.
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Guarda las credenciales del usuario autenticado.
     * Se llama desde LoginController tras un login exitoso.
     */
    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Genera la cabecera de autenticación Basic Auth.
     * Formato: "Basic " + Base64("username:password")
     */
    private String getAuthHeader() {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Realiza una petición POST sin autenticación (para login y registro).
     *
     * @param endpoint ruta del endpoint, ej: "/api/auth/login"
     * @param body     objeto Java que se convertirá a JSON
     * @return HttpResponse con el resultado
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
     * Realiza una petición POST con autenticación (para endpoints protegidos).
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
     * Petición POST con autenticación y sin body (parámetros en la URL).
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
     * Realiza una petición GET con autenticación.
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
     * Realiza una petición PUT con autenticación.
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
     * Realiza una petición DELETE con autenticación.
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
     * Realiza una petición PATCH con autenticación.
     */
    public HttpResponse<String> patch(String endpoint, Object body) throws Exception {
        String json = body != null ? objectMapper.writeValueAsString(body) : "";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", getAuthHeader())
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Deserializa el JSON de una respuesta a un objeto Java.
     *
     * @param response respuesta HTTP
     * @param clazz    clase destino
     */
    public <T> T parseResponse(HttpResponse<String> response, Class<T> clazz) throws Exception {
        return objectMapper.readValue(response.body(), clazz);
    }
}
