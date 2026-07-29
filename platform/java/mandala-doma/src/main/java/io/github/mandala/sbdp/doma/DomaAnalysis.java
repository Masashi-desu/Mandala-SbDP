package io.github.mandala.sbdp.doma;

import java.util.List;

public record DomaAnalysis(
        List<DomaDaoDescriptor> daos,
        List<ExternalSqlMapping> sqlMappings,
        List<String> warnings) {
    public DomaAnalysis {
        daos = List.copyOf(daos == null ? List.of() : daos);
        sqlMappings = List.copyOf(sqlMappings == null ? List.of() : sqlMappings);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
