package com.taskmaster.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * ENTIDAD USERSETTINGS
 *
 * Almacena las preferencias de cada usuario.
 * Relación OneToOne con User — cada usuario tiene exactamente una configuración.
 */
@Entity
@Table(name = "user_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relación con User.
     * @OneToOne    → Un usuario tiene una sola configuración y viceversa
     * @JoinColumn  → Crea la columna "user_id" en la tabla "user_settings"
     *                que apunta al usuario propietario
     */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Días que se guardan los elementos en la papelera antes de borrarse.
     * El usuario puede elegir entre 7, 15 o 30 días desde ajustes.
     * Por defecto: 30 días.
     */
    @Column(nullable = false)
    @Builder.Default
    private int trashRetentionDays = 30;
}
