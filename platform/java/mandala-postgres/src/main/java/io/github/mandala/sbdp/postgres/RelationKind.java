package io.github.mandala.sbdp.postgres;

public enum RelationKind {
    TABLE,
    PARTITIONED_TABLE,
    FOREIGN_TABLE,
    VIEW,
    MATERIALIZED_VIEW
}
