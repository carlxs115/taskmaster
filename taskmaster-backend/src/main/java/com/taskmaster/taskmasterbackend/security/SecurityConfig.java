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
     * <p>Endpoints públicos: {@code /api/auth/**} y {@code /h2-console/**}.<br>
     * El resto de endpoints requieren autenticación Basic Auth.</p>
     *
     * @param http configurador de seguridad HTTP de Spring
     * @return cadena de filtros configurada
     * @throws Exception si ocurre un error durante la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )

                // Habilitamos HTTP Basic Authentication
                // Permite enviar credenciales en la cabecera Authorization: Basic
                .httpBasic(Customizer.withDefaults())

                // Registramos nuestro AuthenticationProvider personalizado
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    /**
     * Configura el proveedor de autenticación con nuestro servicio de usuarios
     * y el codificador de contraseñas BCrypt.
     *
     * @return proveedor de autenticación configurado
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Bean de BCrypt para cifrar y verificar contraseñas.
     * Se inyecta en {@link com.taskmaster.taskmasterbackend.service.UserService}
     * para cifrar la contraseña al registrar un usuario.
     *
     * @return codificador de contraseñas BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expone el {@link AuthenticationManager} como Bean para poder usarlo
     * en {@link com.taskmaster.taskmasterbackend.controller.AuthController}
     * durante el proceso de login.
     *
     * @param config configuración de autenticación de Spring
     * @return gestor de autenticación
     * @throws Exception si ocurre un error al obtenerlo
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
