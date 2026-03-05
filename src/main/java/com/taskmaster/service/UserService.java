package com.taskmaster.service;

import com.taskmaster.model.User;
import com.taskmaster.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * SERVICIO DE USER
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

    public User register(String username, String email, String password) {

        // Validación: username único
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("El nombre de usuario ya está en uso");
        }

        // Validación: email único
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Construimos el usuario con el patrón Builder que nos da Lombok
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password)) // Cifrado BCrypt
                .build();

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
}
