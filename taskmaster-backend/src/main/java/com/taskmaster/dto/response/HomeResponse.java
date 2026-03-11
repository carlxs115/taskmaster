package com.taskmaster.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HomeResponse {
    private List<ProjectWithTasksResponse> projects;
    private List<TaskResponse> personalTasks;
    private List<TaskResponse> estudiosTasks;
    private List<TaskResponse> trabajoTasks;
}
