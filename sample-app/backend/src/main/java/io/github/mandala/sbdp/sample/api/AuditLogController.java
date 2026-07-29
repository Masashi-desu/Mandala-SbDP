package io.github.mandala.sbdp.sample.api;

import io.github.mandala.sbdp.sample.database.entity.AuditLogEntity;
import io.github.mandala.sbdp.sample.service.AuditLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogResponse> recent(
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return auditLogService.recent(limit).stream().map(AuditLogResponse::from).toList();
    }

    public record AuditLogResponse(
            Long id,
            Long userId,
            String action,
            String entityType,
            Long entityId,
            String details,
            LocalDateTime createdAt) {
        static AuditLogResponse from(AuditLogEntity audit) {
            return new AuditLogResponse(
                    audit.getId(),
                    audit.getUserId(),
                    audit.getAction(),
                    audit.getEntityType(),
                    audit.getEntityId(),
                    audit.getDetails(),
                    audit.getCreatedAt());
        }
    }
}
