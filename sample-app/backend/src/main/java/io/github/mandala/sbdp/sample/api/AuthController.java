package io.github.mandala.sbdp.sample.api;

import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.sample.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AuditLogService auditLogService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(AuthenticationManager authenticationManager, AuditLogService auditLogService) {
        this.authenticationManager = authenticationManager;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public AuthenticatedUser login(
            @Valid @RequestBody LoginRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        requestBody.username(), requestBody.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        auditLogService.record(principal.id(), "LOGIN", "user", principal.id(), Map.of());
        return AuthenticatedUser.from(principal);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal AppUserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        auditLogService.record(principal.id(), "LOGOUT", "user", principal.id(), Map.of());
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    @GetMapping("/me")
    public AuthenticatedUser me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return AuthenticatedUser.from(principal);
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 200) String password) {
    }

    public record AuthenticatedUser(Long id, String username, String role) {
        static AuthenticatedUser from(AppUserPrincipal principal) {
            return new AuthenticatedUser(principal.id(), principal.username(), principal.role().name());
        }
    }
}
