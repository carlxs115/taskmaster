package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.WorkLogDTO;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.WorkLog;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio que gestiona los registros de trabajo asociados a las tareas.
 *
 * <p>Permite crear, actualizar, eliminar y consultar registros de trabajo,
 * así como calcular el total de horas invertidas en una tarea incluyendo
 * sus subtareas de forma recursiva.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
public class WorkLogService {

    private final WorkLogRepository workLogRepository;
    private final TaskRepository taskRepository;

    // -------------------------------------------------------------------------
    // Operaciones CRUD
    // -------------------------------------------------------------------------

    /**
     * Crea un nuevo registro de trabajo asociado a una tarea.
     *
     * @param taskId identificador de la tarea
     * @param dto    datos del registro a crear
     * @return registro creado como DTO
     * @throws ResourceNotFoundException si no existe ninguna tarea con ese identificador
     */
    @Transactional
    public WorkLogDTO create(Long taskId, WorkLogDTO dto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con id: " + taskId));

        WorkLog workLog = WorkLog.builder()
                .date(dto.date())
                .activityType(dto.activityType())
                .hours(dto.hours())
                .note(dto.note())
                .task(task)
                .build();

        return toDTO(workLogRepository.save(workLog));
    }

    /**
     * Actualiza un registro de trabajo existente.
     *
     * @param id  identificador del registro
     * @param dto nuevos datos del registro
     * @return registro actualizado como DTO
     * @throws ResourceNotFoundException si no existe ningún registro con ese identificador
     */
    @Transactional
    public WorkLogDTO update(Long id, WorkLogDTO dto) {
        WorkLog workLog = workLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de trabajo no encontrado con id: " + id));

        workLog.setDate(dto.date());
        workLog.setActivityType(dto.activityType());
        workLog.setHours(dto.hours());
        workLog.setNote(dto.note());

        return toDTO(workLogRepository.save(workLog));
    }

    /**
     * Elimina un registro de trabajo por su identificador.
     *
     * @param id identificador del registro a eliminar
     */
    @Transactional
    public void delete(Long id) {
        workLogRepository.deleteById(id);
    }

    /**
     * Devuelve todos los registros de trabajo de una tarea como DTOs.
     *
     * @param taskId identificador de la tarea
     * @return lista de registros como DTOs
     */
    @Transactional(readOnly = true)
    public List<WorkLogDTO> getByTask(Long taskId) {
        return workLogRepository.findByTaskId(taskId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Calcula el total de horas invertidas en una tarea,
     * incluyendo todas sus subtareas de forma recursiva.
     *
     * @param taskId identificador de la tarea raíz
     * @return total de horas acumuladas como {@link BigDecimal}
     * @throws ResourceNotFoundException si no existe ninguna tarea con ese identificador
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalHours(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con id: " + taskId));

        return calculateTotalHours(task);
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Suma las horas registradas en una tarea y todas sus subtareas recursivamente.
     *
     * @param task tarea desde la que calcular el total
     * @return total de horas acumuladas incluyendo subtareas
     */
    private BigDecimal calculateTotalHours(Task task) {
        // sumHoursByTaskId puede devolver null si la tarea no tiene worklogs,
        // usamos BigDecimal.ZERO como valor por defecto para evitar NullPointerException
        BigDecimal total = workLogRepository.sumHoursByTaskId(task.getId());
        if (total == null) total = BigDecimal.ZERO;

        // Sumamos recursivamente las horas de cada subtarea
        if (task.getSubTasks() != null) {
            for (Task subTask : task.getSubTasks()) {
                total = total.add(calculateTotalHours(subTask));
            }
        }
        return total;
    }

    /**
     * Convierte una entidad {@link WorkLog} a su DTO correspondiente.
     * El DTO omite la referencia a la tarea para evitar referencias circulares.
     *
     * @param workLog entidad a convertir
     * @return DTO con los datos del registro
     */
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
