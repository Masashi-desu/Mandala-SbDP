package io.github.mandala.sbdp.postgres;

import java.util.List;
import java.util.Objects;

public record PostgresSchema(
        String name,
        String owner,
        String comment,
        List<PostgresRelation> relations,
        List<SequenceDefinition> sequences,
        List<EnumDefinition> enums,
        List<DomainDefinition> domains,
        List<FunctionDefinition> functions) {
    public PostgresSchema {
        name = Objects.requireNonNull(name, "name");
        owner = Objects.requireNonNullElse(owner, "");
        comment = Objects.requireNonNullElse(comment, "");
        relations = List.copyOf(relations == null ? List.of() : relations);
        sequences = List.copyOf(sequences == null ? List.of() : sequences);
        enums = List.copyOf(enums == null ? List.of() : enums);
        domains = List.copyOf(domains == null ? List.of() : domains);
        functions = List.copyOf(functions == null ? List.of() : functions);
    }
}
