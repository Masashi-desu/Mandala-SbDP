package io.github.mandala.sbdp.sample.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mandala.sbdp.sample.database.dao.AuditLogDao;
import io.github.mandala.sbdp.sample.database.entity.AuditLogEntity;
import io.github.mandala.sbdp.starter.MandalaApplicationService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@MandalaApplicationService
public class AuditLogService {
    private final AuditLogDao auditLogDao;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogDao auditLogDao, ObjectMapper objectMapper) {
        this.auditLogDao = auditLogDao;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(Long userId, String action, String entityType, Long entityId, Map<String, ?> details) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setUserId(userId);
        audit.setAction(action);
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setDetails(toJson(details));
        audit.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        auditLogDao.insert(audit);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntity> recent(int limit) {
        return auditLogDao.selectRecent(limit);
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Audit details cannot be serialized", failure);
        }
    }
}
