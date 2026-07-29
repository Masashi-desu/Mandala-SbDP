package io.github.mandala.sbdp.doma;

public enum DomaOperation {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    BATCH_INSERT,
    BATCH_UPDATE,
    BATCH_DELETE,
    SCRIPT,
    PROCEDURE,
    FUNCTION,
    ARRAY_CREATE,
    UNKNOWN
}
