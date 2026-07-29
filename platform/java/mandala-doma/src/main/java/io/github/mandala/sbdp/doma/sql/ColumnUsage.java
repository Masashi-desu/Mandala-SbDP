package io.github.mandala.sbdp.doma.sql;

public enum ColumnUsage {
    REFERENCED,
    WHERE,
    JOIN,
    INSERT_TARGET,
    UPDATE_TARGET,
    RETURNING
}
