package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link WorkLog}.
 *
 * <p>Proporciona consultas para recuperar registros de trabajo
 * asociados a una tarea y calcular el total de horas invertidas.</p>
 *
 * @author Carlos
 */
public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    /**
     * Devuelve todos los registros de trabajo asociados a una tarea.
     *
     * @param taskId identificador de la tarea
     * @return lista de registros de trabajo de esa tarea
     */
    List<WorkLog> findByTaskId(Long taskId);

    /**
     * Calcula el total de horas registradas para una tarea.
     * Si no hay registros, devuelve cero.
     *
     * @param taskId identificador de la tarea
     * @return suma de horas registradas, o {@code 0} si no hay registros
     */
    @Query("SELECT COALESCE(SUM(w.hours), 0) FROM WorkLog w WHERE w.task.id = :taskId")
    BigDecimal sumHoursByTaskId(@Param("taskId") Long taskId);
}
