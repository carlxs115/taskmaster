package com.taskmaster.taskmasterbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.taskmaster.taskmasterbackend.model.enums.ThemeType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entidad que almacena las preferencias personales de cada usuario.
 *
 * <p>Mantiene una relación uno a uno con {@link User}. Se crea automáticamente
 * al registrar un nuevo usuario y se elimina en cascada si el usuario es borrado.</p>
 *
 * <p>La validación de valores permitidos (p.ej. días de retención válidos)
 * se realiza en los DTOs de request, no en esta entidad.</p>
 *
 * @author Carlos
 */
@Entity
@Table(name = "user_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {

    /** Identificador único de la configuración, generado automáticamente por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Usuario propietario de esta configuración.
     * Carga lazy para no traer el usuario completo al consultar solo sus ajustes.
     * {@code unique = true} garantiza la relación uno a uno a nivel de base de datos.
     */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Número de días que los elementos permanecen en la papelera antes de
     * eliminarse definitivamente por el scheduler {@code TrashScheduler}.
     *
     * <p>Valores permitidos por la interfaz: 7, 15 o 30 días.
     * La validación de que el valor sea uno de estos tres se realiza
     * en el DTO de request correspondiente.</p>
     *
     * <p>Valor por defecto: 30 días.</p>
     */
    @Column(nullable = false)
    @Builder.Default
    private int trashRetentionDays = 30;

    /**
     * Tema visual seleccionado por el usuario para la interfaz JavaFX.
     * Se almacena como String con el nombre del enum para legibilidad en BD.
     *
     * <p>Valor por defecto: {@link ThemeType#AMATISTA}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ThemeType theme = ThemeType.AMATISTA;
}
