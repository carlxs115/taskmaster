package com.taskmaster.taskmasterbackend.controller;

import com.taskmaster.taskmasterbackend.dto.WorkLogDTO;
import com.taskmaster.taskmasterbackend.service.WorkLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/worklogs")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogService workLogService;

    @PostMapping("/task/{taskId}")
    public ResponseEntity<WorkLogDTO> create(
            @PathVariable Long taskId,
            @Valid @RequestBody WorkLogDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workLogService.create(taskId, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkLogDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody WorkLogDTO dto) {
        return ResponseEntity.ok(workLogService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workLogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<WorkLogDTO>> getByTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(workLogService.getByTask(taskId));
    }

    @GetMapping("/task/{taskId}/total")
    public ResponseEntity<BigDecimal> getTotalHours(@PathVariable Long taskId) {
        return ResponseEntity.ok(workLogService.getTotalHours(taskId));
    }
}
