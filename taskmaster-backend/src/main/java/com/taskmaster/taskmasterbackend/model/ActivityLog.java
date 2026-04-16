package com.taskmaster.taskmasterbackend.model;

import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    @Column
    private String entityType; // "TASK", "SUBTASK", "PROJECT", "PROFILE"

    @Column
    private Long entityId; // ID de la entidad afectada (nullable)

    @Column
    private String entityName; // Nombre snapshot (ej: "Mi tarea") para mostrar aunque se borre

    @Column
    private String oldValue; // Para STATUS_CHANGED: valor anterior

    @Column
    private String newValue; // Para STATUS_CHANGED: valor nuevo

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
