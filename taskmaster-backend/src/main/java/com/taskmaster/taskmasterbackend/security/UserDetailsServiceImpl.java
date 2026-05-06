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
 * el proceso de autenticación HTTP Basic para obtener los datos del usuario
 * y verificar su contraseña contra el hash BCrypt almacenado.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Carga un usuario de la base de datos por su username para que Spring Security
     * pueda verificar sus credenciales.
     *
     * <p>El método acepta tanto username como email como identificador de entrada,
     * lo que permite al usuario hacer login con cualquiera de los dos.
     * Spring Security llama a este método automáticamente en cada petición
     * autenticada, antes de ejecutar el controlador correspondiente.</p>
     *
     * <p>El objeto {@link UserDetails} devuelto contiene:</p>
     * <ul>
     *     <li>username - identificador del usuario</li>
     *     <li>password - hash BCrypt almacenado en BD (Spring lo compara internamente)</li>
     *     <li>roles - rol asignado, en este caso siempre {@code USER}</li>
     * </ul>
     *
     * @param username nombre de usuario o email a buscar (lo que el usuario introdujo)
     * @return datos del usuario listos para que Spring Security verifique la contraseña
     * @throws UsernameNotFoundException si no existe ningún usuario con ese username o email
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Buscamos primero por username, y si no existe, intentamos por email.
        // Esto permite al usuario hacer login con cualquiera de los dos campos.
        com.taskmaster.taskmasterbackend.model.User user = userRepository
                .findByUsername(username)
                .or(() -> userRepository.findByEmail(username))

                // SEGURIDAD: el mensaje no incluye el username recibido para evitar
                // que un atacante pueda confirmar qué usuarios existen en el sistema
                // (ataque de enumeración de usuarios).
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));

        // Construimos el objeto UserDetails que Spring Security usará internamente.
        // La contraseña ya viene hasheada con BCrypt desde la base de datos,
        // Spring Security se encarga de compararla con la recibida en la petición.
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles("USER") // todos los usuarios de TaskMaster tienen el mismo rol
                .build();
    }
}
