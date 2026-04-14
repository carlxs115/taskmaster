package com.taskmaster.service;

import com.taskmaster.dto.response.UserStatsResponse;
import com.taskmaster.model.enums.ActionType;
import com.taskmaster.model.enums.TaskStatus;
import com.taskmaster.model.User;
import com.taskmaster.model.UserSettings;
import com.taskmaster.repository.ProjectRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * SERVICIO DE USER
 *
 * Gestiona el registro y búsqueda de usuarios.
 * Al registrar un usuario se crea automáticamente su configuración
 * por defecto (UserSettings) con papelera de 30 días.
 *
 * @Service      → Le dice a Spring que esta clase es un servicio.
 *                 Spring la crea automáticamente y la gestiona como un Bean.
 *
 * @RequiredArgsConstructor → Lombok genera un constructor con todos los campos
 *                            marcados como "final". Así Spring inyecta las
 *                            dependencias automáticamente (inyección por constructor).
 *                            Es la forma recomendada en Spring Boot moderno,
 *                            mejor que usar @Autowired.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ActivityLogService activityLogService;

    /**
     * Registra un nuevo usuario.
     *
     * Pasos:
     * 1. Comprueba que el username no esté ya en uso
     * 2. Comprueba que el email no esté ya en uso
     * 3. Cifra la contraseña con BCrypt
     * 4. Crea la configuración por defecto (UserSettings)
     * 5. Guarda el usuario en la BD
     */
    public User register(String username, String email, String password, LocalDate birthDate) {

        // Validación: username único
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("El nombre de usuario ya está en uso");
        }

        // Validación: email único
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Creamos la configuración por defecto del usuario.
        // trashRetentionDays = 30 por defecto (definido en la entidad con @Builder.Default)
        UserSettings settings = UserSettings.builder().build();

        // Construimos el usuario con el patrón Builder que nos da Lombok
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password)) // Cifrado BCrypt
                .birthDate(birthDate)
                .settings(settings)
                .build();

        // Vinculamos la configuración al usuario antes de guardar.
        settings.setUser(user);

        return userRepository.save(user);
    }

    /**
     * Busca un usuario por su username.
     *
     * @throws RuntimeException si no existe
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));
    }

    /**
     * Busca un usuario por su id.
     *
     * @throws RuntimeException si no existe
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));
    }

    /**
     * Actualiza username, email y fecha de nacimiento del usuario.
     * Valida que el nuevo username/email no estén en uso por otro usuario.
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
     * Cambia la contraseña del usuario.
     * Valida que la contraseña actual sea correcta antes de cambiarla.
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
     * Elimina permanentemente la cuenta del usuario y todos sus datos.
     * Gracias al cascade ALL en User, se borran en cascada:
     * proyectos, tareas y settings.
     */
    public void deleteAccount(Long userId, String password) {
        User user = findById(userId);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("La contraseña no es correcta");
        }

        userRepository.delete(user);
    }
}
