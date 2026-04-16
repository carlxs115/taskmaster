package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.WorkLogDTO;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.WorkLog;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkLogService {

    private final WorkLogRepository workLogRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public WorkLogDTO create(Long taskId, WorkLogDTO dto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        WorkLog workLog = WorkLog.builder()
                .date(dto.date())
                .activityType(dto.activityType())
                .hours(dto.hours())
                .note(dto.note())
                .task(task)
                .build();

        WorkLog saved = workLogRepository.save(workLog);
        return toDTO(saved);
    }

    @Transactional
    public WorkLogDTO update(Long id, WorkLogDTO dto) {
        WorkLog workLog = workLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("WorkLog not found: " + id));

        workLog.setDate(dto.date());
        workLog.setActivityType(dto.activityType());
        workLog.setHours(dto.hours());
        workLog.setNote(dto.note());

        return toDTO(workLogRepository.save(workLog));
    }

    @Transactional
    public void delete(Long id) {
        workLogRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<WorkLogDTO> getByTask(Long taskId) {
        return workLogRepository.findByTaskId(taskId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalHours(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        return calculateTotalHours(task);
    }

    // Suma las horas de la tarea + todas sus subtareas recursivamente
    private BigDecimal calculateTotalHours(Task task) {
        BigDecimal total = workLogRepository.sumHoursByTaskId(task.getId());

        if (task.getSubTasks() != null) {
            for (Task subTask : task.getSubTasks()) {
                total = total.add(calculateTotalHours(subTask));
            }
        }

        return total;
    }

    private WorkLogDTO toDTO(WorkLog workLog) {
        return new WorkLogDTO(
                workLog.getId(),
                workLog.getDate(),
                workLog.getActivityType(),
                workLog.getHours(),
                workLog.getNote()
        );
    }
}
