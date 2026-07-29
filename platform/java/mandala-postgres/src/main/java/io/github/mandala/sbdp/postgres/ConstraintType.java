package io.github.mandala.sbdp.postgres;

public enum ConstraintType {
    PRIMARY_KEY,
    FOREIGN_KEY,
    UNIQUE,
    CHECK,
    EXCLUSION,
    UNKNOWN
}
