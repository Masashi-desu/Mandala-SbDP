package io.github.mandala.sbdp.sample.database.dao;

import io.github.mandala.sbdp.sample.database.entity.TaskEntity;
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
public interface TaskDao {
    @Select
    List<TaskEntity> selectByProjectId(Long projectId);

    @Select
    Optional<TaskEntity> selectById(Long id);

    @Insert(sqlFile = true)
    int insert(TaskEntity task);

    @Select
    Long selectCurrentIdentity();

    @Update(sqlFile = true)
    int update(TaskEntity task);

    @Delete(sqlFile = true)
    int delete(TaskEntity task);
}
