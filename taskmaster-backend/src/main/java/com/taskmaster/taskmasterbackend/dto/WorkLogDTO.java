package com.taskmaster.taskmasterbackend.dto;

import com.taskmaster.taskmasterbackend.model.enums.ActivityType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkLogDTO (
        Long id,
        @NotNull LocalDate date,
        @NotNull ActivityType activityType,
        @NotNull @DecimalMin("0.1") BigDecimal hours,
        String note
) {}
