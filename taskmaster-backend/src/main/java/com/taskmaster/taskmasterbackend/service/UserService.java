package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.response.UserStatsResponse;
import com.taskmaster.taskmasterbackend.exception.BusinessException;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.UserSettings;
import com.taskmaster.taskmasterbackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Servicio que gestiona el registro, perfil y seguridad de los usuarios.
 *
 * <p>Al registrar un nuevo usuario se crea automáticamente su configuración
 * por defecto ({@link UserSettings}) con un periodo de retención de papelera
 * de 30 días y el tema visual por defecto.</p>
 *
 * @author Carlos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogService activityLogService;
    private final WorkLogRepository workLogRepository;

    // -------------------------------------------------------------------------
    // Registro y búsqueda
    // -------------------------------------------------------------------------

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * <p>Pasos que realiza:</p>
     * <ol>
     *   <li>Valida que el username y email no estén ya en uso</li>
     *   <li>Cifra la contraseña con BCrypt</li>
     *   <li>Crea la configuración por defecto del usuario ({@link UserSettings})</li>
     *   <li>Persiste el usuario con su configuración en cascada</li>
     * </ol>
     *
     * @param username  nombre de usuario elegido
     * @param email     correo electrónico
     * @param password  contraseña en texto plano (se cifrará antes de persistir)
     * @param birthDate fecha de nacimiento
     * @return usuario creado y persistido
     * @throws BusinessException si el username o el email ya están en uso
     */
    @Transactional
    public User register(String username, String email, String password, LocalDate birthDate) {

        // Comprobamos unicidad antes de intentar persistir para dar mensajes claros
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("El nombre de usuario ya está en uso");
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("El email ya está registrado");
        }

        // Creamos la configuración por defecto. Se vinculará al usuario justo después.
        UserSettings settings = UserSettings.builder().build();

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password)) // Cifrado BCrypt
                .birthDate(birthDate)
                .settings(settings)
                .build();

        // La relación es bidireccional: UserSettings también necesita la referencia al usuario
        settings.setUser(user);

        // Al guardar user, JPA persiste también settings en cascada (CascadeType.ALL)
        return userRepository.save(user);
    }

    /**
     * Busca un usuario por su nombre de usuario.
     *
     * @param username nombre de usuario a buscar
     * @return usuario encontrado
     * @throws ResourceNotFoundException si no existe ningún usuario con ese nombre
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)

                // Mensaje genérico para no confirmar qué usernames existen
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    /**
     * Busca un usuario por su identificador numérico.
     *
     * @param id identificador del usuario
     * @return usuario encontrado
     * @throws ResourceNotFoundException si no existe ningún usuario con ese identificador
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }

    // -------------------------------------------------------------------------
    // Perfil
    // -------------------------------------------------------------------------

    /**
     * Actualiza el perfil del usuario (username, email y fecha de nacimiento).
     * Valida que el nuevo username y email no estén en uso por otro usuario distinto.
     *
     * @param userId    identificador del usuario
     * @param username  nuevo nombre de usuario
     * @param email     nuevo correo electrónico
     * @param birthDate nueva fecha de nacimiento
     * @return usuario actualizado
     * @throws BusinessException si el username o el email ya están en uso por otro usuario
     */
    @Transactional
    public User updateProfile(Long userId, String username, String email, LocalDate birthDate) {
        User user = findById(userId);

        // Solo validamos unicidad si el valor ha cambiado respecto al actual.
        // Si el usuario envía su propio username actual, no debe fallar.
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new BusinessException("El nombre de usuario ya está en uso");
        }
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new BusinessException("El email ya está en uso");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setBirthDate(birthDate);

        User saved = userRepository.save(user);

        // Registramos la actualización en el historial de actividad
        activityLogService.log(userId, ActionType.PROFILE_UPDATED);

        return saved;
    }

    // -------------------------------------------------------------------------
    // Estadísticas
    // -------------------------------------------------------------------------

    /**
     * Calcula y devuelve las estadísticas de actividad del usuario en tiempo real,
     * consultando directamente los contadores en base de datos para mayor eficiencia.
     *
     * @param userId identificador del usuario
     * @return objeto con totales de tareas por estado y tasa de completado
     */
    public UserStatsResponse getStats(Long userId) {

        // Contamos cada estado con queries individuales optimizadas (usan índice user_id)
        long total      = taskRepository.countByUserIdAndDeletedFalse(userId);
        long completed  = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.DONE);
        long pending    = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.TODO);
        long inProgress = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.IN_PROGRESS);
        long cancelled  = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.CANCELLED);
        long projects   = projectRepository.countByUserIdAndDeletedFalse(userId);

        // Tasa de completado: porcentaje de tareas completadas sobre el total.
        // Si no hay tareas evitamos división por cero devolviendo 0.
        int  rate       = total > 0 ? (int) (completed * 100 / total) : 0;

        return UserStatsResponse.builder()
                .totalTasks(total)
                .completedTasks(completed)
                .pendingTasks(pending)
                .inProgressTasks(inProgress)
                .cancelledTasks(cancelled)
                .totalProjects(projects)
                .completionRate(rate)
                .build();
    }

    // -------------------------------------------------------------------------
    // Seguridad de cuenta
    // -------------------------------------------------------------------------

    /**
     * Cambia la contraseña del usuario tras verificar la contraseña actual.
     *
     * @param userId          identificador del usuario
     * @param currentPassword contraseña actual en texto plano para verificación
     * @param newPassword     nueva contraseña en texto plano (se cifrará con BCrypt)
     * @throws BusinessException si la contraseña actual no es correcta
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findById(userId);

        // Comparamos la contraseña recibida contra el hash BCrypt almacenado.
        // BCrypt se encarga de aplicar la sal y comparar correctamente.
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("La contraseña actual no es correcta");
        }

        // Ciframos la nueva contraseña antes de persistirla
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        activityLogService.log(userId, ActionType.PASSWORD_CHANGED);
        log.info("Contraseña actualizada para usuario id: {}", userId);
    }

    /**
     * Elimina permanentemente la cuenta del usuario y todos sus datos asociados.
     *
     * <p>Gracias al {@code CascadeType.ALL} definido en {@link com.taskmaster.taskmasterbackend.model.User},
     * se eliminan automáticamente en cascada sus proyectos, tareas y configuración.
     * Los worklogs y logs de actividad se eliminan manualmente antes porque no están
     * en la cascada directa del usuario.</p>
     *
     * @param userId   identificador del usuario
     * @param password contraseña actual para confirmar la operación
     * @throws BusinessException si la contraseña de confirmación no es correcta
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = findById(userId);

        // Requerimos confirmación con contraseña para evitar borrados accidentales
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("La contraseña no es correcta");
        }

        // Eliminamos worklogs y activity logs manualmente porque están vinculados
        // a las tareas del usuario, no directamente al usuario (no entran en la cascada)
        workLogRepository.deleteByTaskUserId(userId);
        activityLogRepository.deleteByUserId(userId);

        // Al borrar el usuario, JPA elimina en cascada proyectos, tareas y settings
        userRepository.delete(user);

        log.info("Cuenta eliminada para usuario id: {}", userId);
    }
}
