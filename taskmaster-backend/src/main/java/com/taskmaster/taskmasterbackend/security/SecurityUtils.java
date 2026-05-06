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
     * Obtiene el identificador numérico del usuario autenticado a partir
     * de su {@link UserDetails}, consultando la base de datos por username.
     *
     * <p>Este método se usa en los controladores para obtener el userId
     * del usuario que está haciendo la petición, sin tener que repetir
     * la lógica de búsqueda en cada endpoint.</p>
     *
     * @param userDetails usuario autenticado inyectado automáticamente por Spring Security
     * @return identificador del usuario
     * @throws UsernameNotFoundException si el usuario autenticado ya no existe en la BD
     *         (caso extremo: usuario eliminado mientras tenía sesión activa)
     */
    public Long getUserId(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)

                // SEGURIDAD: mensaje genérico para no confirmar qué usernames existen.
                // Este caso es prácticamente imposible en uso normal (el usuario
                // está autenticado, por tanto existe), pero lo manejamos igualmente.
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
