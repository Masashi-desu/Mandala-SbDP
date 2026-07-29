package io.github.mandala.sbdp.postgres;

import java.sql.SQLException;
import java.util.List;

@FunctionalInterface
public interface CatalogQueryExecutor {
    List<CatalogRow> query(String sql) throws SQLException;
}
