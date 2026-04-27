package com.taskmaster.taskmasterbackend.security;

import com.taskmaster.taskmasterbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link UserDetailsService} que carga usuarios desde la base de datos.
 *
 * <p>Spring Security llama automáticamente a {@link #loadUserByUsername} durante
 * el proceso de login para obtener los datos del usuario y verificar su contraseña.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Carga un usuario de la base de datos por su nombre de usuario.
     * Devuelve un {@link UserDetails} con username, contraseña cifrada y rol {@code USER}.
     *
     * @param username nombre de usuario a buscar
     * @return datos del usuario para Spring Security
     * @throws UsernameNotFoundException si no existe ningún usuario con ese nombre
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.taskmaster.taskmasterbackend.model.User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPassword())  // Ya viene cifrada con BCrypt
                .roles("USER")
                .build();
    }


}
