package io.github.mandala.sbdp.sample.service;

import io.github.mandala.sbdp.sample.database.dao.ProjectDao;
import io.github.mandala.sbdp.sample.database.entity.ProjectEntity;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.starter.MandalaApplicationService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@MandalaApplicationService
public class ProjectService {
    private final ProjectDao projectDao;
    private final ProjectAccessPolicy accessPolicy;
    private final AuditLogService auditLogService;

    public ProjectService(
            ProjectDao projectDao,
            ProjectAccessPolicy accessPolicy,
            AuditLogService auditLogService) {
        this.projectDao = projectDao;
        this.accessPolicy = accessPolicy;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> list(AppUserPrincipal actor) {
        return projectDao.selectAccessible(actor.id(), actor.isAdmin());
    }

    @Transactional(readOnly = true)
    public ProjectEntity get(Long id, AppUserPrincipal actor) {
        return accessPolicy.requireAccessible(id, actor);
    }

    /** Creates an owned project and records the creation in the audit log atomically. */
    @Transactional
    public ProjectEntity create(String name, String description, AppUserPrincipal actor) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ProjectEntity project = new ProjectEntity();
        project.setOwnerId(actor.id());
        project.setName(name.trim());
        project.setDescription(normalize(description));
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectDao.insert(project);
        project.setId(projectDao.selectCurrentIdentity());
        auditLogService.record(actor.id(), "CREATE", "project", project.getId(), Map.of("name", project.getName()));
        return project;
    }

    @Transactional
    public ProjectEntity update(Long id, String name, String description, AppUserPrincipal actor) {
        ProjectEntity project = accessPolicy.requireAccessible(id, actor);
        project.setName(name.trim());
        project.setDescription(normalize(description));
        project.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        projectDao.update(project);
        auditLogService.record(actor.id(), "UPDATE", "project", id, Map.of("name", project.getName()));
        return project;
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        ProjectEntity project = accessPolicy.requireAccessible(id, actor);
        projectDao.delete(project);
        auditLogService.record(actor.id(), "DELETE", "project", id, Map.of("name", project.getName()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
