package com.taskmaster.taskmasterfrontend.util;

import com.taskmaster.taskmasterfrontend.service.ApiService;

/**
 * APPCONTEXT
 *
 * Clase singleton que mantiene el estado global de la aplicación.
 *
 * Singleton -> solo existe una instancia de esta clase en toda la app.
 * Todos los controladores acceden a la misma instancia de ApiService
 * y a los datos del usuario autenticado.
 *
 * Se usa así desde cualquier controlador:
 *   AppContext.getInstance().getApiService()
 *   AppContext.getInstance().getCurrentUserId()
 */
public class AppContext {

    // Instancia única del singleton
    private static AppContext instance;

    private final ApiService apiService;

    // Datos del usuario autenticado tras el login
    private Long currentUserId;
    private String currentUsername;
    private String currentPassword;

    /**
     * Constructor privado - nadie puede crear instancias desde fuera.
     */
    public AppContext() {
        this.apiService = new ApiService();
    }

    public String getCurrentPassword() { return currentPassword; }

    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    /**
     * Devuelve la instancia única del AppContext.
     * Si no existe todavía, la crea.
     */
    public static AppContext getInstance() {
        if (instance == null) {
            instance = new AppContext();
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }

    public Long getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    /**
     * Limpia los datos del usuario al cerrar sesión.
     */
    public void logout() {
        this.currentUserId = null;
        this.currentUsername = null;
        this.apiService.setCredentials(null, null);
        this.currentPassword = null;
    }
}
