package com.taskmaster.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProjectWithTasksResponse {
    private Long id;
    private String name;
    private String category;
    private List<TaskResponse> tasks;
}
