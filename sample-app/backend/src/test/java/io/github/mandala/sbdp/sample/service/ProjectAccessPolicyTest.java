package io.github.mandala.sbdp.sample.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.mandala.sbdp.sample.database.dao.ProjectDao;
import io.github.mandala.sbdp.sample.database.entity.ProjectEntity;
import io.github.mandala.sbdp.sample.domain.ForbiddenOperationException;
import io.github.mandala.sbdp.sample.domain.ResourceNotFoundException;
import io.github.mandala.sbdp.sample.domain.Role;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAccessPolicyTest {
    private static final AppUserPrincipal OWNER =
            new AppUserPrincipal(2L, "owner", "hash", Role.USER, true);
    private static final AppUserPrincipal OTHER_USER =
            new AppUserPrincipal(3L, "other", "hash", Role.USER, true);
    private static final AppUserPrincipal ADMIN =
            new AppUserPrincipal(1L, "admin", "hash", Role.ADMIN, true);

    @Mock
    ProjectDao projectDao;

    @Test
    void allowsOwnerAndAdministratorButRejectsOtherUser() {
        ProjectEntity project = new ProjectEntity();
        project.setId(10L);
        project.setOwnerId(OWNER.id());
        when(projectDao.selectById(10L)).thenReturn(Optional.of(project));
        ProjectAccessPolicy policy = new ProjectAccessPolicy(projectDao);

        assertThat(policy.requireAccessible(10L, OWNER)).isSameAs(project);
        assertThat(policy.requireAccessible(10L, ADMIN)).isSameAs(project);
        assertThatThrownBy(() -> policy.requireAccessible(10L, OTHER_USER))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void reportsMissingProjectBeforePermissionCheck() {
        when(projectDao.selectById(99L)).thenReturn(Optional.empty());
        ProjectAccessPolicy policy = new ProjectAccessPolicy(projectDao);

        assertThatThrownBy(() -> policy.requireAccessible(99L, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
