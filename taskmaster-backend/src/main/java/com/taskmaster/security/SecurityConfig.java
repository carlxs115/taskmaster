package com.taskmaster.security;

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
 * SECURITYCONFIG
 *
 * Clase central de configuración de Spring Security.
 *
 * @Configuration -> Esta clase define Beans de Spring
 * @EnableWebSecurity -> Activa la seguridad web de Spring Security
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    /**
     * SECURITYFILTERCHAIN
     *
     * Define qué endpoints están protegidos y cuáles son públicos.
     * Cada petición HTTP pasa por esta cadena de filtros antes de
     * llegar al controlador.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /**
                 * Desactivamos CSRF (Cross-Site Request Forgery).
                 * CSRF protege aplicaciones web con formularios HTML.
                 * En una app de escritorio JavaFX que consume una API REST
                 * no es necesario - no hay navegador ni cookies de sesión.
                 */
                .csrf(AbstractHttpConfigurer::disable)

                /**
                 * Configuración de endpoints:
                 *      - /api/auth/** -> público (login y registro no requieren autenticación)
                 *      - /h2-console/** -> público (consola de BD para desarrollo)
                 *      - cualquier otra ruta -> requiere autenticación
                 */
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated()
                )

                /**
                 * Política de sesión STATELESS.
                 * No guardamos sesiones en el servidor - cada petición
                 * debe incluir las credenciales del usuario.
                 * Apropiado para APIs REST consumidas desde JavaFX.
                 */
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                /**
                 * Necesario para que la consola H2 se muestre correctamente.
                 * H2 usa iframes internamente y Spring Security los bloquea por defecto.
                 */
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
     * AUTHENTICATIONPROVIDER
     *
     * Le dice a Spring Security cómo autenticar usuarios:
     * - Usando nuestro UserDetailsServiceImpl para cargarlos de la BD
     * - Usando BCrypt para comparar contraseñas
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * PASSWORDENCODER
     *
     * Bean de BCrypt para cifrar y verificar contraseñas.
     * Se inyecta automáticamente en UserService para cifrar la contraseña al registrar un usuario.
     *
     * BCrypt es un algoritmo de hash diseñado específicamente
     * para contraseñas - es lento a propósito para dificultar
     * ataques de fuerza bruta.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AUTHENTICATIONMANAGER
     *
     * Componente de Spring Security que coordina el proceso de login.
     * Lo exponemos como Bean para poder usarlo en AuthController cuando el usuario haga login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
