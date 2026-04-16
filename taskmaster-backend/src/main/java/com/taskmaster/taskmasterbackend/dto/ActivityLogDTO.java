package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ActivityLogDTO {
    private Long id;
    private ActionType actionType;
    private String entityType;
    private Long entityId;
    private String entityName;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}
