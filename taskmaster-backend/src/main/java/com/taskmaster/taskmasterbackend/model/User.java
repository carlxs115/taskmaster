package com.taskmaster.taskmasterbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entidad que representa un usuario registrado en la aplicación.
 *
 * <p>Es la entidad principal del sistema. Todas las tareas, proyectos
 * y configuraciones pertenecen a un usuario. La contraseña se almacena
 * cifrada con BCrypt, nunca en texto plano.</p>
 *
 * <p><b>Nota sobre validaciones:</b> las anotaciones {@code @NotBlank} y
 * {@code @Email} aquí sirven como documentación de restricciones de negocio,
 * pero la validación real que protege la API se realiza en los DTOs de request
 * mediante {@code @Valid} en los controladores.</p>
 *
 * @author Carlos
 */
@Entity
@Table(
        name = "users",

        // Índice en email porque el login permite autenticarse con email,
        // y UserDetailsServiceImpl hace findByEmail en cada petición autenticada.
        indexes = @Index(name = "idx_users_email", columnList = "email")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Identificador único del usuario, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre de usuario único. Se usa como identificador principal de autenticación.
     * No puede estar vacío ni repetirse entre usuarios.
     */
    @Column(unique = true, nullable = false)
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    /**
     * Correo electrónico único del usuario.
     * Se permite su uso como alternativa al username en el login.
     */
    @Column(unique = true, nullable = false)
    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    /**
     * Contraseña del usuario almacenada como hash BCrypt.
     * El cifrado se realiza en {@link com.taskmaster.taskmasterbackend.service.UserService}
     * antes de persistir. Nunca se almacena en texto plano.
     *
     * <p><b>Nota de seguridad:</b> este campo nunca se incluye en respuestas al cliente.
     * Los DTOs de respuesta ({@code UserResponse}) lo omiten explícitamente.</p>
     */
    @Column(nullable = false)
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    /** Fecha de nacimiento del usuario. Se usa para mostrar el banner de cumpleaños. */
    @Column(nullable = false)
    private LocalDate birthDate;

    /**
     * Fecha y hora de registro del usuario.
     * Se asigna automáticamente en {@link #onCreate()} y nunca puede modificarse
     * ({@code updatable = false}).
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Ruta relativa al fichero de imagen de perfil dentro del directorio de avatares.
     * Ejemplo: {@code "a1b2c3d4-e5f6-7890-abcd-ef1234567890.png"}.
     * Longitud máxima: 255 caracteres (límite por defecto de @Column).
     *
     * <p>Si es {@code null}, el usuario no tiene foto de perfil y la interfaz
     * mostrará sus iniciales como avatar por defecto.</p>
     */
    @Column
    private String avatarPath;

    /**
     * Lista de proyectos del usuario.
     * {@code CascadeType.ALL} y {@code orphanRemoval = true} garantizan que
     * al eliminar un usuario se eliminan también todos sus proyectos.
     * {@code @JsonIgnore} evita serializar esta lista en respuestas JSON,
     * previniendo referencias circulares y exposición de datos no deseados.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Project> projects;

    /**
     * Lista de tareas del usuario. Incluye tanto tareas independientes
     * como tareas asociadas a proyectos.
     * Si el usuario es eliminado, todas sus tareas se eliminan en cascada.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> tasks;

    /**
     * Configuración personal del usuario (tema, idioma, retención de papelera, etc.).
     * Se crea automáticamente al registrar el usuario y se elimina en cascada
     * si el usuario es eliminado.
     */
    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserSettings settings;

    /**
     * Asigna automáticamente la fecha y hora de registro antes de persistir
     * la entidad por primera vez. JPA llama a este método automáticamente
     * gracias a {@code @PrePersist}.
     */
    @PrePersist
    protected void onCreate(){

        // Se asigna aquí para garantizar que refleja el momento real
        // de persistencia, independientemente de cuándo se construyó el objeto.
        this.createdAt = LocalDateTime.now();
    }
}
