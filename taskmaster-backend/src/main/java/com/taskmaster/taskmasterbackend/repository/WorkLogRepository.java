package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
@Repository
public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    /**
     * Devuelve todos los registros de trabajo asociados a una tarea,
     * ordenados por fecha de creación en base de datos.
     *
     * @param taskId identificador de la tarea
     * @return lista de registros de trabajo de esa tarea
     */
    List<WorkLog> findByTaskId(Long taskId);

    /**
     * Calcula el total de horas registradas para una tarea.
     * {@code COALESCE} garantiza que se devuelva {@code 0} cuando no hay
     * registros, evitando que el resultado sea {@code null}.
     *
     * <p><b>Nota:</b> {@code WorkLogService.calculateTotalHours} incluye
     * también una protección adicional contra {@code null} por robustez.</p>
     *
     * @param taskId identificador de la tarea
     * @return suma de horas registradas, o {@code BigDecimal.ZERO} si no hay registros
     */
    @Query("SELECT COALESCE(SUM(w.hours), 0) FROM WorkLog w WHERE w.task.id = :taskId")
    BigDecimal sumHoursByTaskId(@Param("taskId") Long taskId);

    /**
     * Elimina todos los registros de trabajo asociados a las tareas de un usuario.
     * Llamado desde {@code UserService.deleteAccount} antes de eliminar la cuenta
     * para limpiar datos que no entran en la cascada directa del usuario.
     *
     * <p>Spring Data aplica {@code @Transactional} automáticamente en métodos
     * de modificación, por lo que no es necesario declararlo explícitamente.</p>
     *
     * @param userId identificador del usuario propietario de las tareas
     */
    void deleteByTaskUserId(Long userId);
}
