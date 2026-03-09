package com.taskmaster.security;

import com.taskmaster.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * USERDETAILSSERVICEIMPL
 *
 * Spring Security necesita saber cómo cargar un usuario desde la BD
 * cuando alguien intenta hacer login. Para eso exige que implementemos
 * la interfaz UserDetailsService con el método loadUserByUsername().
 *
 * Cuando el usuario introduce su username y password en el login,
 * Spring Security llama automáticamente a loadUserByUsername() para
 * obtener los datos del usuario y comparar la contraseña.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Carga un usuario de la BD por su username.
     *
     * Spring Security llama a este método automáticamente durante el login.
     * Devuelve un UserDetails con username, password y roles.
     *
     * @throws UsernameNotFoundException si el usuario no existe en la BD
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Buscamos el usuario en la BD
        com.taskmaster.model.User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        /**
         * User.withUsername() es el builder de Spring Security para crear
         * un objeto UserDetails - no confundir con nuestra entidad User.
         *
         * roles("USER") → asignamos el rol básico de usuario.
         * Spring Security lo convierte internamente a "ROLE_USER".
         * En el futuro podríamos añadir "ROLE_ADMIN" para administradores.
         */
        return User.withUsername(user.getUsername())
                .password(user.getPassword())  // Ya viene cifrada con BCrypt
                .roles("USER")
                .build();
    }


}
