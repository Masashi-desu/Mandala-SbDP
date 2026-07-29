package io.github.mandala.sbdp.sample.database.dao;

import io.github.mandala.sbdp.sample.database.entity.ProjectEntity;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface ProjectDao {
    @Select
    List<ProjectEntity> selectAccessible(Long userId, boolean admin);

    @Select
    Optional<ProjectEntity> selectById(Long id);

    @Insert(sqlFile = true)
    int insert(ProjectEntity project);

    @Select
    Long selectCurrentIdentity();

    @Update(sqlFile = true)
    int update(ProjectEntity project);

    @Delete(sqlFile = true)
    int delete(ProjectEntity project);
}
