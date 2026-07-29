package io.github.mandala.sbdp.postgres;

import java.util.List;
import java.util.Objects;

public record PostgresRelation(
        String schema,
        String name,
        RelationKind kind,
        String owner,
        String comment,
        String definition,
        String parentSchema,
        String parentTable,
        boolean rowSecurityEnabled,
        boolean rowSecurityForced,
        List<ColumnDefinition> columns,
        List<ConstraintDefinition> constraints,
        List<IndexDefinition> indexes,
        List<TriggerDefinition> triggers,
        List<PolicyDefinition> policies) {
    public PostgresRelation {
        schema = Objects.requireNonNull(schema, "schema");
        name = Objects.requireNonNull(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        owner = value(owner);
        comment = value(comment);
        definition = value(definition);
        parentSchema = value(parentSchema);
        parentTable = value(parentTable);
        columns = List.copyOf(columns == null ? List.of() : columns);
        constraints = List.copyOf(constraints == null ? List.of() : constraints);
        indexes = List.copyOf(indexes == null ? List.of() : indexes);
        triggers = List.copyOf(triggers == null ? List.of() : triggers);
        policies = List.copyOf(policies == null ? List.of() : policies);
    }

    public String qualifiedName() {
        return schema + "." + name;
    }

    private static String value(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
