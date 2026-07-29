package io.github.mandala.sbdp.sample.database.dao;

import io.github.mandala.sbdp.sample.database.entity.UserEntity;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface UserDao {
    @Select
    Optional<UserEntity> selectById(Long id);

    @Select
    Optional<UserEntity> selectByUsername(String username);
}
