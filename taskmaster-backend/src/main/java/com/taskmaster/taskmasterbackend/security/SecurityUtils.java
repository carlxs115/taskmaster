package com.taskmaster.taskmasterbackend.security;

import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * SECURITYUTILS
 *
 * Clase de utilidad para obtener el usuario autenticado en los controladores.
 * Evita repetir el mismo código en cada controlador.
 *
 * @Component -> Spring la gestiona como un Bean pero sin ser @Service ni @Controller
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /**
     * Obtiene el userId del usuario autenticado.
     * Spring Security nos da el UserDetails con el username,
     * y desde ahí buscamos el id en la BD.
     */
    public Long getUserId(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + userDetails.getUsername()
                ));
    }
}
