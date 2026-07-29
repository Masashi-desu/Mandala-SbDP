package io.github.mandala.sbdp.sample.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mandala.sbdp.sample.database.entity.TaskEntity;
import io.github.mandala.sbdp.sample.domain.Role;
import io.github.mandala.sbdp.sample.domain.TaskStatus;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.sample.security.RestAccessDeniedHandler;
import io.github.mandala.sbdp.sample.security.RestAuthenticationEntryPoint;
import io.github.mandala.sbdp.sample.security.SecurityConfiguration;
import io.github.mandala.sbdp.sample.service.TaskService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@Import({SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TaskControllerTest {
    private static final AppUserPrincipal USER =
            new AppUserPrincipal(2L, "local-user", "hash", Role.USER, true);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TaskService taskService;

    @Test
    void validatesTaskCreation() throws Exception {
        mockMvc.perform(post("/api/projects/1/tasks")
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void rejectsUnknownTaskStatusAsInvalidRequest() throws Exception {
        mockMvc.perform(patch("/api/tasks/7/status")
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void changesTaskStatus() throws Exception {
        TaskEntity task = task(7L, TaskStatus.DONE);
        when(taskService.changeStatus(eq(7L), eq(TaskStatus.DONE), any())).thenReturn(task);

        mockMvc.perform(patch("/api/tasks/7/status")
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        verify(taskService).changeStatus(eq(7L), eq(TaskStatus.DONE), any());
    }

    private static TaskEntity task(Long id, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setProjectId(1L);
        task.setTitle("Test");
        task.setDescription("");
        task.setStatus(status.name());
        task.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        task.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return task;
    }
}
