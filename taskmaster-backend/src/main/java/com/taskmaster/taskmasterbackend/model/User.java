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
 * ENTIDAD USER
 *
 * @Entity  → Esta clase es una tabla en la BD
 * @Table   → Nombre de la tabla (usamos "users" porque "user" es palabra reservada en SQL)
 * @Data    → Lombok genera getters, setters, toString, equals y hashCode automáticamente
 * @Builder → Permite crear objetos así: User.builder().username("carlos").build()
 * @NoArgsConstructor → Constructor vacío, necesario para JPA
 * @AllArgsConstructor → Constructor con todos los campos
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * @Id                  → Es la clave primaria de la tabla
     * @GeneratedValue      → La BD genera el valor automáticamente (autoincremental)
     * GenerationType.IDENTITY → Delega la generación al motor de BD (H2, PostgreSQL...)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Column(unique = true, nullable = false)
     * unique    → No puede haber dos usuarios con el mismo username
     * nullable  → No puede estar vacío en la BD
     */
    @Column(unique = true, nullable = false)
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    @Column(unique = true, nullable = false)
    @Email(message = "El email no es válido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    /**
     * La contraseña se guardará cifrada con BCrypt, nunca en texto plano.
     * El cifrado se hace en el servicio, no aquí.
     */
    @Column(nullable = false)
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    @Column(nullable = false)
    private LocalDate birthDate;

    /**
     * @Column(updatable = false) → Este campo se asigna una vez y nunca se actualiza
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Ruta relativa al fichero de imagen de perfil dentro del directorio de avatares.
     * Ejemplo: "a1b2c3d4-e5f6-7890-abcd-ef1234567890.png"
     * Si es null, el usuario no tiene foto de perfil y se mostrarán sus iniciales.
     * El fichero físico vive en la carpeta configurada en taskmaster.storage.avatars-dir.
     */
    @Column(length = 255)
    private String avatarPath;

    /**
     * Relación con proyectos: un usuario tiene muchos proyectos.
     * mappedBy = "user" → le dice a JPA que la relación ya está definida en la entidad Project con el campo "user"
     * cascade = ALL      → si borramos un usuario, se borran sus proyectos también
     * orphanRemoval      → si un proyecto se desvincula del usuario, se borra de la BD
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Project> projects;

    /**
     * Relación con tareas personales del usuario (sin proyecto).
     * cascade ALL + orphanRemoval → al borrar el usuario se borran todas sus tareas.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Task> tasks;

    /**
     * Configuración del usuario.
     * Se crea automáticamente al registrar el usuario en UserService.
     * cascade = ALL → si se borra el usuario, se borra también su configuración
     */
    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserSettings settings;

    /**
     * @PrePersist → Se ejecuta automáticamente justo antes de guardar en la BD por primera vez
     * Así no tenemos que asignar la fecha manualmente nunca.
     */
    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
    }
}
