package io.github.mandala.sbdp.sample.api;

import io.github.mandala.sbdp.sample.database.entity.ProjectEntity;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.sample.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return projectService.list(principal).stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return ProjectResponse.from(projectService.get(id, principal));
    }

    /** Accepts a validated project request and returns the newly created project resource. */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        ProjectEntity created = projectService.create(request.name(), request.description(), principal);
        return ResponseEntity.created(URI.create("/api/projects/" + created.getId()))
                .body(ProjectResponse.from(created));
    }

    @PutMapping("/{id}")
    public ProjectResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ProjectResponse.from(projectService.update(id, request.name(), request.description(), principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        projectService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2_000) String description) {
    }

    public record ProjectResponse(
            Long id,
            Long ownerId,
            String name,
            String description,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        static ProjectResponse from(ProjectEntity project) {
            return new ProjectResponse(
                    project.getId(),
                    project.getOwnerId(),
                    project.getName(),
                    project.getDescription(),
                    project.getCreatedAt(),
                    project.getUpdatedAt());
        }
    }
}
