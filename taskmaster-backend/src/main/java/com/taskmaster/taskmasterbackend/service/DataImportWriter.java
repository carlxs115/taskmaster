package com.taskmaster.taskmasterbackend.service;

import com.taskmaster.taskmasterbackend.dto.response.ImportResultResponse;
import com.taskmaster.taskmasterbackend.dto.transfer.DataExportFile;
import com.taskmaster.taskmasterbackend.dto.transfer.DependencyData;
import com.taskmaster.taskmasterbackend.dto.transfer.ProjectData;
import com.taskmaster.taskmasterbackend.dto.transfer.TaskData;
import com.taskmaster.taskmasterbackend.dto.transfer.WorkLogData;
import com.taskmaster.taskmasterbackend.exception.ResourceNotFoundException;
import com.taskmaster.taskmasterbackend.model.Project;
import com.taskmaster.taskmasterbackend.model.Task;
import com.taskmaster.taskmasterbackend.model.User;
import com.taskmaster.taskmasterbackend.model.WorkLog;
import com.taskmaster.taskmasterbackend.model.enums.ActionType;
import com.taskmaster.taskmasterbackend.model.enums.TaskCategory;
import com.taskmaster.taskmasterbackend.model.enums.TaskPriority;
import com.taskmaster.taskmasterbackend.model.enums.TaskStatus;
import com.taskmaster.taskmasterbackend.repository.ProjectRepository;
import com.taskmaster.taskmasterbackend.repository.TaskRepository;
import com.taskmaster.taskmasterbackend.repository.UserRepository;
import com.taskmaster.taskmasterbackend.repository.WorkLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Escribe en la base de datos un fichero de importación ya validado, en una sola transacción.
 *
 * <p>Es un bean aparte de {@link DataTransferService} para que la lectura y la validación (que
 * reciben hasta 32 MB por la red) ocurran fuera de la transacción: SQLite solo admite un
 * escritor y no conviene mantenerlo bloqueado mientras llega el cuerpo.</p>
 *
 * <p>La importación es aditiva: crea entidades nuevas, con ids nuevos, y nunca borra ni
 * modifica lo que el usuario ya tiene. El fichero no trae ids, usuario ni dueños: toda
 * entidad nueva pertenece al usuario autenticado. Si algo falla, Spring deshace todo,
 * también la actividad registrada.</p>
 *
 * @author Carlos
 */
@Service
@RequiredArgsConstructor
public class DataImportWriter {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WorkLogRepository workLogRepository;
    private final ActivityLogService activityLogService;

    /** Contadores y mapa de referencias de una importación. */
    private static final class Run {
        final Long userId;
        final User user;
        final Map<String, Task> byRef = new HashMap<>();
        int projects;
        int tasks;
        int workLogs;

        Run(Long userId, User user) {
            this.userId = userId;
            this.user = user;
        }
    }

    /**
     * Crea los proyectos, tareas, subtareas, registros de trabajo y dependencias del fichero.
     *
     * @param userId identificador del usuario propietario de todo lo creado
     * @param file   fichero ya validado con {@link DataImportValidator#validate}
     * @return número de elementos creados
     * @throws ResourceNotFoundException si el usuario no existe
     */
    @Transactional
    public ImportResultResponse write(Long userId, DataExportFile file) {
        // Los ids de tasks se reutilizan y el esquema no tiene claves foráneas efectivas: las
        // filas huérfanas de task_dependencies y work_logs se quedarían las tareas nuevas
        // (SEG-8, SEG-9). Se limpian antes de que puedan reutilizar esos ids.
        taskRepository.deleteOrphanTaskReferences();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));
        Run run = new Run(userId, user);

        if (file.projects() != null) {
            for (ProjectData data : file.projects()) {
                writeProject(data, run);
            }
        }
        if (file.tasks() != null) {
            for (TaskData data : file.tasks()) {
                writeTask(data, null, null, null, run);
            }
        }

        int dependencies = writeDependencies(file.dependencies(), run);
        return new ImportResultResponse(run.projects, run.tasks, run.workLogs, dependencies);
    }

    private void writeProject(ProjectData data, Run run) {
        Project project = projectRepository.save(Project.builder()
                .name(data.name())
                .description(data.description())
                .category(data.category())
                .status(data.status() != null ? data.status() : TaskStatus.TODO)
                .priority(data.priority() != null ? data.priority() : TaskPriority.MEDIUM)
                .estimatedDuration(data.estimatedDuration())
                .user(run.user)
                .deleted(false)
                .build());
        run.projects++;
        activityLogService.log(run.userId, ActionType.PROJECT_CREATED, "PROJECT",
                project.getId(), project.getName());

        if (data.tasks() != null) {
            for (TaskData task : data.tasks()) {
                writeTask(task, project, null, null, run);
            }
        }
    }

    /**
     * Crea una tarea y, a continuación, sus registros y subtareas (preorden: el padre siempre
     * antes que los hijos). Las subtareas llevan el proyecto de su raíz.
     */
    private void writeTask(TaskData data, Project project, Task parent, TaskCategory parentCategory, Run run) {
        // Misma regla que TaskService.createTask: en un proyecto manda la categoría del proyecto
        TaskCategory category;
        if (project != null) {
            category = project.getCategory();
        } else if (data.category() != null) {
            category = data.category();
        } else {
            category = parentCategory != null ? parentCategory : TaskCategory.PERSONAL;
        }

        Task task = taskRepository.save(Task.builder()
                .title(data.title())
                .description(data.description())
                .status(data.status() != null ? data.status() : TaskStatus.TODO)
                .priority(data.priority() != null ? data.priority() : TaskPriority.MEDIUM)
                .category(category)
                .dueDate(data.dueDate())
                .estimatedDuration(data.estimatedDuration())
                .user(run.user)
                .project(project)
                .parentTask(parent)
                .deleted(false)
                .build());
        run.tasks++;
        boolean isSubtask = parent != null;
        activityLogService.log(run.userId,
                isSubtask ? ActionType.SUBTASK_CREATED : ActionType.TASK_CREATED,
                isSubtask ? "SUBTASK" : "TASK",
                task.getId(), task.getTitle());

        if (data.ref() != null) {
            run.byRef.put(data.ref(), task);
        }

        if (data.workLogs() != null && !data.workLogs().isEmpty()) {
            List<WorkLog> workLogs = new ArrayList<>();
            for (WorkLogData log : data.workLogs()) {
                workLogs.add(WorkLog.builder()
                        .date(log.date())
                        .activityType(log.activityType())
                        .hours(log.hours())
                        .note(log.note())
                        .task(task)
                        .build());
            }
            workLogRepository.saveAll(workLogs);
            run.workLogs += workLogs.size();
        }

        if (data.subtasks() != null) {
            for (TaskData subtask : data.subtasks()) {
                writeTask(subtask, project, task, category, run);
            }
        }
    }

    /**
     * Enlaza las dependencias sobre las entidades gestionadas (se escriben al hacer flush).
     * Las repetidas se ignoran. No se registra {@code DEPENDENCY_ADDED}: sería ruido.
     */
    private int writeDependencies(List<DependencyData> dependencies, Run run) {
        if (dependencies == null) {
            return 0;
        }
        Set<DependencyData> unique = new LinkedHashSet<>(dependencies);
        for (DependencyData dependency : unique) {
            Task task = run.byRef.get(dependency.task());
            Task dependsOn = run.byRef.get(dependency.dependsOn());
            task.getDependencies().add(dependsOn);
        }
        return unique.size();
    }
}
