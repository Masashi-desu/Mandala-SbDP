package io.github.mandala.sbdp.sample.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mandala.sbdp.sample.domain.Role;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.sample.security.RestAccessDeniedHandler;
import io.github.mandala.sbdp.sample.security.RestAuthenticationEntryPoint;
import io.github.mandala.sbdp.sample.security.SecurityConfiguration;
import io.github.mandala.sbdp.sample.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthControllerTest {
    private static final AppUserPrincipal USER =
            new AppUserPrincipal(2L, "local-user", "hash", Role.USER, true);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthenticationManager authenticationManager;

    @MockitoBean
    AuditLogService auditLogService;

    @Test
    void logsInAndPersistsSecurityContextInSession() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(USER, null, USER.getAuthorities()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"local-user\",\"password\":\"mandala-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("local-user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        org.hamcrest.Matchers.notNullValue()));
        verify(auditLogService).record(eq(2L), eq("LOGIN"), eq("user"), eq(2L), any());
    }

    @Test
    void mapsBadCredentialsToStableApiError() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"local-user\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void validatesLoginAndExposesCurrentUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(get("/api/auth/me").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void logsOutAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(user(USER)))
                .andExpect(status().isNoContent());
        verify(auditLogService).record(eq(2L), eq("LOGOUT"), eq("user"), eq(2L), any());
    }
}
