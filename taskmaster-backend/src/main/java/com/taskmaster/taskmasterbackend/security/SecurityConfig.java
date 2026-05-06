package com.taskmaster.taskmasterbackend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración central de Spring Security para la aplicación.
 *
 * <p>Define qué endpoints son públicos y cuáles requieren autenticación,
 * establece la política de sesiones sin estado (stateless) apropiada para
 * una API REST consumida desde un cliente JavaFX, y configura la autenticación
 * mediante HTTP Basic con BCrypt.</p>
 *
 * <p>Base de datos: SQLite (local). No hay consola web de base de datos
 * expuesta, por lo que no se necesita ninguna excepción de seguridad
 * para rutas de administración.</p>
 *
 * @author Carlos
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Define la cadena de filtros de seguridad que procesa cada petición HTTP.
     *
     * <p>Endpoints públicos: solo {@code /api/auth/**} (registro y login).<br>
     * El resto de endpoints requieren autenticación HTTP Basic.</p>
     *
     * @param http configurador de seguridad HTTP de Spring
     * @return cadena de filtros configurada
     * @throws Exception si ocurre un error durante la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                // Desactivamos CSRF porque la API es stateless y usa HTTP Basic,
                // no cookies de sesión. Sin cookies de sesión no existe el vector
                // de ataque CSRF, por lo que desactivarlo es seguro en este contexto.
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Solo registro y login son accesibles sin autenticación.
                        // Cualquier otro endpoint requiere credenciales válidas.
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )

                // Política stateless: el servidor no guarda ninguna sesión entre peticiones.
                // El frontend JavaFX debe incluir las credenciales en cada petición mediante
                // la cabecera: Authorization: Basic <usuario:contraseña en Base64>
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .headers(headers -> headers
                        // Desactivamos la carga en iframes completamente.
                        // Esta es una API REST pura consumida por JavaFX, nunca
                        // se necesita incrustar nada en un <iframe> de un navegador.
                        // Esto protege contra ataques de tipo clickjacking.
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                )

                // HTTP Basic: el frontend envía las credenciales codificadas en Base64
                // en la cabecera Authorization de cada petición HTTP.
                // Spring Security las decodifica y las verifica automáticamente
                // usando el authenticationProvider registrado abajo.
                .httpBasic(Customizer.withDefaults())

                // Registramos nuestro proveedor de autenticación personalizado,
                // que sabe cómo cargar usuarios desde SQLite y verificar
                // contraseñas cifradas con BCrypt.
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    /**
     * Configura el proveedor de autenticación con nuestro servicio de usuarios
     * y el codificador de contraseñas BCrypt.
     *
     * <p>Spring usará este proveedor cada vez que necesite verificar
     * las credenciales de una petición entrante.</p>
     *
     * @return proveedor de autenticación configurado
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Usamos el constructor nuevo que recibe el PasswordEncoder directamente,
        // evitando el uso del constructor vacío deprecado en versiones recientes
        // de Spring Security
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder());

        // Le indicamos cómo cargar los datos del usuario desde la base de datos
        // por su username
        provider.setUserDetailsService(userDetailsService);

        return provider;
    }

    /**
     * Bean de BCrypt para cifrar y verificar contraseñas.
     *
     * <p>BCrypt es un algoritmo de hashing lento por diseño, lo que lo hace
     * resistente a ataques de fuerza bruta. Incluye sal (salt) automática,
     * por lo que dos usuarios con la misma contraseña tendrán hashes distintos.</p>
     *
     * <p>Se inyecta en {@link com.taskmaster.taskmasterbackend.service.UserService}
     * para cifrar la contraseña al registrar un nuevo usuario.</p>
     *
     * @return codificador de contraseñas BCrypt con factor de coste por defecto (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {

        // El factor de coste por defecto es 10, que es un balance adecuado
        // entre seguridad y rendimiento para una app de escritorio.
        // Se puede subir a 12 para mayor seguridad si el rendimiento lo permite.
        return new BCryptPasswordEncoder();
    }

    /**
     * Expone el {@link AuthenticationManager} como Bean para poder usarlo
     * en {@link com.taskmaster.taskmasterbackend.controller.AuthController}
     * durante el proceso de login manual.
     *
     * @param config configuración de autenticación proporcionada por Spring
     * @return gestor de autenticación
     * @throws Exception si ocurre un error al obtenerlo
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
