package io.github.mandala.sbdp.postgres;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JdbcCatalogQueryExecutor implements CatalogQueryExecutor {
    private final Connection connection;

    public JdbcCatalogQueryExecutor(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public List<CatalogRow> query(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(512);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                List<CatalogRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        Object value = resultSet.getObject(column);
                        if (value instanceof Array array) {
                            value = array.getArray();
                            array.free();
                        }
                        values.put(metadata.getColumnLabel(column), value);
                    }
                    rows.add(new CatalogRow(values));
                }
                return List.copyOf(rows);
            }
        }
    }
}
