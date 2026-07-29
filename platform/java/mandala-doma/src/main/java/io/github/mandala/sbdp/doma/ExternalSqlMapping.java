package io.github.mandala.sbdp.doma;

import io.github.mandala.sbdp.doma.sql.SqlStatementAnalysis;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ExternalSqlMapping(
        String stableId,
        String daoMethodId,
        Path sqlFile,
        DomaSqlTemplate template,
        List<SqlStatementAnalysis> statements,
        List<String> warnings) {
    public ExternalSqlMapping {
        stableId = Objects.requireNonNull(stableId, "stableId");
        daoMethodId = Objects.requireNonNull(daoMethodId, "daoMethodId");
        sqlFile = Objects.requireNonNull(sqlFile, "sqlFile").toAbsolutePath().normalize();
        template = Objects.requireNonNull(template, "template");
        statements = List.copyOf(statements == null ? List.of() : statements);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
