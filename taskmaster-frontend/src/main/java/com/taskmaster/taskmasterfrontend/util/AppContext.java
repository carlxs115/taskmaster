package com.taskmaster.taskmasterfrontend.util;

import com.taskmaster.taskmasterfrontend.service.ApiService;

import java.time.LocalDate;

/**
 * Singleton que mantiene el estado global de la sesión activa en TaskMaster.
 *
 * <p>Proporciona acceso centralizado al {@link ApiService} y a los datos del
 * usuario autenticado (identificador, nombre, contraseña, fecha de nacimiento
 * y estado del avatar). Todos los controladores acceden a esta instancia
 * mediante {@link #getInstance()}.</p>
 *
 * @author Carlos
 */
public class AppContext {

    // Instancia única del singleton
    private static AppContext instance;

    private final ApiService apiService;

    // Datos del usuario autenticado tras el login
    private Long currentUserId;
    private String currentUsername;
    private String currentPassword;

    private LocalDate currentBirthDate;

    private boolean hasAvatar;

    /**
     * Constructor privado que inicializa el {@link ApiService}.
     * El acceso externo se realiza exclusivamente mediante {@link #getInstance()}.
     */
    public AppContext() {
        this.apiService = new ApiService();
    }

    /**
     * Devuelve la instancia única del singleton, creándola si aún no existe.
     *
     * @return Instancia global de {@link AppContext}.
     */
    public static AppContext getInstance() {
        if (instance == null) {
            instance = new AppContext();
        }
        return instance;
    }

    /**
     * Devuelve el {@link ApiService} para realizar peticiones al backend.
     *
     * @return Instancia del servicio HTTP.
     */
    public ApiService getApiService() {
        return apiService;
    }

    /**
     * Devuelve el identificador del usuario autenticado.
     *
     * @return Identificador del usuario, o {@code null} si no hay sesión activa.
     */
    public Long getCurrentUserId() {
        return currentUserId;
    }

    /** @param currentUserId Identificador del usuario autenticado. */
    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }

    /**
     * Devuelve el nombre de usuario del usuario autenticado.
     *
     * @return Nombre de usuario, o {@code null} si no hay sesión activa.
     */
    public String getCurrentUsername() {
        return currentUsername;
    }

    /** @param currentUsername Nombre de usuario autenticado. */
    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    /**
     * Devuelve la contraseña en texto plano del usuario autenticado,
     * necesaria para renovar las credenciales HTTP Basic tras un cambio de datos.
     *
     * @return Contraseña actual, o {@code null} si no hay sesión activa.
     */
    public String getCurrentPassword() { return currentPassword; }

    /** @param currentPassword Contraseña actual del usuario autenticado. */
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    /**
     * Devuelve la fecha de nacimiento del usuario autenticado.
     *
     * @return Fecha de nacimiento, o {@code null} si no se ha cargado.
     */
    public LocalDate getCurrentBirthDate() {
        return currentBirthDate;
    }

    /** @param birthDate Fecha de nacimiento del usuario autenticado. */
    public void setCurrentBirthDate(LocalDate birthDate) {
        this.currentBirthDate = birthDate;
    }

    /**
     * Indica si el usuario autenticado tiene una foto de avatar subida.
     *
     * @return {@code true} si el usuario tiene avatar.
     */
    public boolean hasAvatar() { return hasAvatar; }

    /** @param hasAvatar {@code true} si el usuario tiene avatar subido. */
    public void setHasAvatar(boolean hasAvatar) { this.hasAvatar = hasAvatar; }

    /**
     * Limpia todos los datos de la sesión activa al cerrar sesión,
     * incluyendo credenciales y datos del usuario.
     */
    public void logout() {
        this.currentUserId = null;
        this.currentUsername = null;
        this.apiService.setCredentials(null, null);
        this.currentPassword = null;
        this.currentBirthDate = null;
        this.hasAvatar = false;
    }
}
