package com.taskmaster.taskmasterbackend.security;

import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Utilidad de seguridad para obtener el identificador del usuario autenticado.
 *
 * <p>Centraliza la lógica de resolución del {@code userId} a partir del
 * {@link UserDetails} inyectado por Spring Security, evitando duplicar
 * este código en cada controlador.</p>
 *
 * @author Carlos
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /**
     * Obtiene el identificador del usuario autenticado a partir de su {@link UserDetails}.
     *
     * @param userDetails usuario autenticado inyectado por Spring Security
     * @return identificador del usuario
     * @throws UsernameNotFoundException si no existe ningún usuario con ese username
     */
    public Long getUserId(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + userDetails.getUsername()
                ));
    }
}
