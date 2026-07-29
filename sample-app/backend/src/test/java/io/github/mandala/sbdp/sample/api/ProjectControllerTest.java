package io.github.mandala.sbdp.sample.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mandala.sbdp.sample.database.entity.ProjectEntity;
import io.github.mandala.sbdp.sample.domain.ForbiddenOperationException;
import io.github.mandala.sbdp.sample.domain.ResourceNotFoundException;
import io.github.mandala.sbdp.sample.domain.Role;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.sample.security.RestAccessDeniedHandler;
import io.github.mandala.sbdp.sample.security.RestAuthenticationEntryPoint;
import io.github.mandala.sbdp.sample.security.SecurityConfiguration;
import io.github.mandala.sbdp.sample.service.ProjectService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ProjectControllerTest {
    private static final AppUserPrincipal USER =
            new AppUserPrincipal(2L, "local-user", "hash", Role.USER, true);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProjectService projectService;

    @Test
    void rejectsUnauthenticatedApiRequestWithJsonError() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.path").value("/api/projects"));
    }

    @Test
    void validatesProjectInputAndReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void returnsEmptyArrayForProjectZeroState() throws Exception {
        when(projectService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/projects").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createsProject() throws Exception {
        ProjectEntity project = project(42L);
        when(projectService.create(any(), any(), any())).thenReturn(project);

        mockMvc.perform(post("/api/projects")
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Documentation\",\"description\":\"Trace it\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("Documentation"));
    }

    @Test
    void mapsMissingAndForbiddenProjectsToDistinctErrors() throws Exception {
        when(projectService.get(404L, USER)).thenThrow(new ResourceNotFoundException("Project", 404L));
        when(projectService.get(403L, USER)).thenThrow(new ForbiddenOperationException("No project access"));

        mockMvc.perform(get("/api/projects/404").with(user(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        mockMvc.perform(get("/api/projects/403").with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    private static ProjectEntity project(Long id) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setOwnerId(USER.id());
        project.setName("Documentation");
        project.setDescription("Trace it");
        project.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        project.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return project;
    }
}
