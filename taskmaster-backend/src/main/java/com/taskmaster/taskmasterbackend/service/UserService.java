package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.response.UserStatsResponse;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.UserSettings;
import com.taskmaster.taskmasterbackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Servicio que gestiona el registro, autenticación y datos de los usuarios.
 *
 * <p>Al registrar un nuevo usuario se crea automáticamente su configuración
 * por defecto ({@link UserSettings}) con un periodo de retención de papelera de 30 días.</p>
 *
 * @author Carlos
 */
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

    /**
     * Registra un nuevo usuario en el sistema.
     * Valida que el username y el email no estén ya en uso, cifra la contraseña
     * con BCrypt y crea la configuración por defecto del usuario.
     *
     * @param username  nombre de usuario elegido
     * @param email     correo electrónico
     * @param password  contraseña en texto plano (se cifrará antes de persistir)
     * @param birthDate fecha de nacimiento
     * @return usuario creado y persistido
     * @throws RuntimeException si el username o el email ya están en uso
     */
    public User register(String username, String email, String password, LocalDate birthDate) {

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("El nombre de usuario ya está en uso");
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        UserSettings settings = UserSettings.builder().build();

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password)) // Cifrado BCrypt
                .birthDate(birthDate)
                .settings(settings)
                .build();

        settings.setUser(user);

        return userRepository.save(user);
    }

    /**
     * Busca un usuario por su nombre de usuario.
     *
     * @param username nombre de usuario a buscar
     * @return usuario encontrado
     * @throws RuntimeException si no existe ningún usuario con ese nombre
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));
    }

    /**
     * Busca un usuario por su identificador.
     *
     * @param id identificador del usuario
     * @return usuario encontrado
     * @throws RuntimeException si no existe ningún usuario con ese identificador
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));
    }

    /**
     * Actualiza el perfil del usuario (username, email y fecha de nacimiento).
     * Valida que el nuevo username y email no estén en uso por otro usuario.
     *
     * @param userId    identificador del usuario
     * @param username  nuevo nombre de usuario
     * @param email     nuevo correo electrónico
     * @param birthDate nueva fecha de nacimiento
     * @return usuario actualizado
     * @throws RuntimeException si el username o el email ya están en uso por otro usuario
     */
    public User updateProfile(Long userId, String username, String email, LocalDate birthDate) {
        User user = findById(userId);

        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new RuntimeException("El nombre de usuario ya está en uso");
        }
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está en uso");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setBirthDate(birthDate);

        User saved = userRepository.save(user);
        activityLogService.log(userId, ActionType.PROFILE_UPDATED);
        return saved;
    }

    /**
     * Calcula y devuelve las estadísticas de actividad del usuario en tiempo real.
     *
     * @param userId identificador del usuario
     * @return estadísticas del usuario
     */
    public UserStatsResponse getStats(Long userId) {
        long total      = taskRepository.countByUserIdAndDeletedFalse(userId);
        long completed  = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.DONE);
        long pending    = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.TODO);
        long inProgress = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.IN_PROGRESS);
        long cancelled  = taskRepository.countByUserIdAndStatusAndDeletedFalse(userId, TaskStatus.CANCELLED);
        long projects   = projectRepository.countByUserIdAndDeletedFalse(userId);
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

    /**
     * Cambia la contraseña del usuario tras verificar la contraseña actual.
     *
     * @param userId          identificador del usuario
     * @param currentPassword contraseña actual en texto plano
     * @param newPassword     nueva contraseña en texto plano
     * @throws RuntimeException si la contraseña actual no es correcta
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findById(userId);
        System.out.println(">>> currentPassword recibido: " + currentPassword);
        System.out.println(">>> matches: " + passwordEncoder.matches(currentPassword, user.getPassword()));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("La contraseña no es correcta");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        activityLogService.log(userId, ActionType.PASSWORD_CHANGED);
    }

    /**
     * Elimina permanentemente la cuenta del usuario y todos sus datos asociados.
     * Gracias al {@code cascade = ALL} en {@link com.taskmaster.taskmasterbackend.model.User},
     * se eliminan en cascada sus proyectos, tareas y configuración.
     *
     * @param userId   identificador del usuario
     * @param password contraseña actual para confirmar la operación
     * @throws RuntimeException si la contraseña no es correcta
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = findById(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("La contraseña no es correcta");
        }
        workLogRepository.deleteByTaskUserId(userId);
        activityLogRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }
}
