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
 * <p><b>Nota de seguridad:</b> la contraseña se mantiene en memoria en texto
 * plano porque HTTP Basic requiere enviarla en cada petición. Este riesgo es
 * aceptable para una app de escritorio monousuario local, donde el proceso
 * está aislado en la máquina del propio usuario.</p>
 *
 * <p><b>Nota de concurrencia:</b> este singleton no es thread-safe por diseño,
 * ya que JavaFX es monohilo y todas las interacciones ocurren en el hilo
 * de la aplicación (JavaFX Application Thread).</p>
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
     * @return instancia global de {@link AppContext}
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
     * @return instancia del servicio HTTP
     */
    public ApiService getApiService() {
        return apiService;
    }

    /**
     * Devuelve el identificador del usuario autenticado.
     *
     * @return identificador del usuario, o {@code null} si no hay sesión activa
     */
    public Long getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Establece el identificador del usuario autenticado.
     *
     * @param currentUserId identificador del usuario
     */
    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }

    /**
     * Devuelve el nombre de usuario del usuario autenticado.
     *
     * @return nombre de usuario, o {@code null} si no hay sesión activa
     */
    public String getCurrentUsername() {
        return currentUsername;
    }

    /**
     * Establece el nombre de usuario autenticado.
     *
     * @param currentUsername nombre de usuario
     */
    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    /**
     * Devuelve la contraseña en texto plano del usuario autenticado.
     * Es necesaria para construir la cabecera HTTP Basic en cada petición,
     * ya que el protocolo no tiene estado y no usa cookies de sesión.
     *
     * @return contraseña actual, o {@code null} si no hay sesión activa
     */
    public String getCurrentPassword() { return currentPassword; }

    /**
     * Establece la contraseña del usuario autenticado.
     *
     * @param currentPassword contraseña en texto plano
     */
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    /**
     * Devuelve la fecha de nacimiento del usuario autenticado.
     *
     * @return fecha de nacimiento, o {@code null} si no se ha cargado
     */
    public LocalDate getCurrentBirthDate() {
        return currentBirthDate;
    }

    /**
     * Establece la fecha de nacimiento del usuario autenticado.
     *
     * @param birthDate fecha de nacimiento
     */
    public void setCurrentBirthDate(LocalDate birthDate) {
        this.currentBirthDate = birthDate;
    }

    /**
     * Indica si el usuario autenticado tiene una foto de avatar subida.
     *
     * @return {@code true} si el usuario tiene avatar
     */
    public boolean hasAvatar() { return hasAvatar; }

    /**
     * Establece si el usuario autenticado tiene avatar subido.
     *
     * @param hasAvatar {@code true} si el usuario tiene avatar
     */
    public void setHasAvatar(boolean hasAvatar) { this.hasAvatar = hasAvatar; }

    /**
     * Limpia todos los datos de la sesión activa al cerrar sesión.
     * Elimina credenciales, datos del usuario y el estado del avatar.
     * El {@link ApiService} también limpia sus credenciales internas.
     */
    public void logout() {
        this.currentUserId = null;
        this.currentUsername = null;
        this.currentPassword = null;
        this.currentBirthDate = null;
        this.hasAvatar = false;

        // Limpiamos las credenciales del ApiService para que las siguientes
        // peticiones no se envíen con las credenciales del usuario anterior
        this.apiService.setCredentials(null, null);
    }
}
