package com.taskmaster.taskmasterbackend.repository;

import com.taskmaster.taskmasterbackend.model.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    List<WorkLog> findByTaskId(Long taskId);

    @Query("SELECT COALESCE(SUM(w.hours), 0) FROM WorkLog w WHERE w.task.id = :taskId")
    BigDecimal sumHoursByTaskId(@Param("taskId") Long taskId);
}
