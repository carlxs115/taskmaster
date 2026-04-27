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

    /** Usuario propietario de esta configuración. Cada usuario tiene exactamente una configuración. */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Número de días que los elementos permanecen en la papelera antes de eliminarse definitivamente.
     * El usuario puede elegir entre 7, 15 o 30 días desde la pantalla de ajustes.
     * Valor por defecto: 30 días.
     */
    @Column(nullable = false)
    @Builder.Default
    private int trashRetentionDays = 30;

    /**
     * Tema visual seleccionado por el usuario.
     * Se almacena como el nombre del enum {@link com.taskmaster.taskmasterbackend.model.enums.ThemeType}.
     * Valor por defecto: {@code "AMATISTA"}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ThemeType theme = ThemeType.AMATISTA;
}
