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
 * @author Carlos
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Identificador único del usuario, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre de usuario. Debe ser único y no puede estar vacío. */
    @Column(unique = true, nullable = false)
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    /** Correo electrónico del usuario. Debe ser único y tener formato válido. */
    @Column(unique = true, nullable = false)
    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    /**
     * Contraseña del usuario almacenada cifrada con BCrypt.
     * El cifrado se realiza en la capa de servicio, nunca aquí.
     */
    @Column(nullable = false)
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    /** Fecha de nacimiento del usuario. */
    @Column(nullable = false)
    private LocalDate birthDate;

    /** Fecha y hora de registro. Se asigna automáticamente al persistir y no puede modificarse. */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Ruta relativa al fichero de imagen de perfil dentro del directorio de avatares.
     * Ejemplo: {@code "a1b2c3d4-e5f6-7890-abcd-ef1234567890.png"}.
     * Si es {@code null}, el usuario no tiene foto de perfil y se mostrarán sus iniciales.
     */
    @Column(length = 255)
    private String avatarPath;

    /**
     * Lista de proyectos del usuario.
     * Si el usuario es eliminado, todos sus proyectos se eliminan en cascada.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Project> projects;

    /**
     * Lista de tareas del usuario.
     * Incluye tanto tareas sin proyecto como tareas asociadas a proyectos.
     * Si el usuario es eliminado, todas sus tareas se eliminan en cascada.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> tasks;

    /**
     * Configuración personal del usuario.
     * Se crea automáticamente al registrar el usuario.
     * Si el usuario es eliminado, su configuración se elimina en cascada.
     */
    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserSettings settings;

    /**
     * Asigna automáticamente la fecha y hora de registro antes de persistir la entidad.
     */
    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }
}
