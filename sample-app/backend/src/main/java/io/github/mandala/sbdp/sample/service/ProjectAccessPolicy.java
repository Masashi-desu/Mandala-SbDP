package io.github.mandala.sbdp.sample.service;

import io.github.mandala.sbdp.sample.database.dao.ProjectDao;
import io.github.mandala.sbdp.sample.database.entity.ProjectEntity;
import io.github.mandala.sbdp.sample.domain.ForbiddenOperationException;
import io.github.mandala.sbdp.sample.domain.ResourceNotFoundException;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import org.springframework.stereotype.Component;

@Component
public class ProjectAccessPolicy {
    private final ProjectDao projectDao;

    public ProjectAccessPolicy(ProjectDao projectDao) {
        this.projectDao = projectDao;
    }

    public ProjectEntity requireAccessible(Long projectId, AppUserPrincipal actor) {
        ProjectEntity project = projectDao.selectById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!actor.isAdmin() && !project.getOwnerId().equals(actor.id())) {
            throw new ForbiddenOperationException("You do not have access to this project");
        }
        return project;
    }
}
