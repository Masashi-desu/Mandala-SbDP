package io.github.mandala.sbdp.sample.database.dao;

import io.github.mandala.sbdp.sample.database.entity.AuditLogEntity;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface AuditLogDao {
    @Insert(sqlFile = true)
    int insert(AuditLogEntity auditLog);

    @Select
    List<AuditLogEntity> selectRecent(int limit);
}
