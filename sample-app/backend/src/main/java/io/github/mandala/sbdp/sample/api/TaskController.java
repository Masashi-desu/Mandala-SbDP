package io.github.mandala.sbdp.sample.api;

import io.github.mandala.sbdp.sample.database.entity.TaskEntity;
import io.github.mandala.sbdp.sample.domain.TaskStatus;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.sample.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/projects/{projectId}/tasks")
    public List<TaskResponse> list(
            @PathVariable Long projectId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.list(projectId, principal).stream().map(TaskResponse::from).toList();
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        TaskEntity created = taskService.create(
                projectId,
                request.title(),
                request.description(),
                request.assigneeId(),
                request.dueDate(),
                principal);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.getId()))
                .body(TaskResponse.from(created));
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return TaskResponse.from(taskService.get(id, principal));
    }

    @PutMapping("/tasks/{id}")
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return TaskResponse.from(taskService.update(
                id,
                request.title(),
                request.description(),
                request.status(),
                request.assigneeId(),
                request.dueDate(),
                principal));
    }

    @PatchMapping("/tasks/{id}/status")
    public TaskResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return TaskResponse.from(taskService.changeStatus(id, request.status(), principal));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        taskService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5_000) String description,
            Long assigneeId,
            @FutureOrPresent LocalDate dueDate) {
    }

    public record UpdateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5_000) String description,
            @NotNull TaskStatus status,
            Long assigneeId,
            @FutureOrPresent LocalDate dueDate) {
    }

    public record ChangeStatusRequest(@NotNull TaskStatus status) {
    }

    public record TaskResponse(
            Long id,
            Long projectId,
            String title,
            String description,
            TaskStatus status,
            Long assigneeId,
            LocalDate dueDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        static TaskResponse from(TaskEntity task) {
            return new TaskResponse(
                    task.getId(),
                    task.getProjectId(),
                    task.getTitle(),
                    task.getDescription(),
                    TaskStatus.valueOf(task.getStatus()),
                    task.getAssigneeId(),
                    task.getDueDate(),
                    task.getCreatedAt(),
                    task.getUpdatedAt());
        }
    }
}
