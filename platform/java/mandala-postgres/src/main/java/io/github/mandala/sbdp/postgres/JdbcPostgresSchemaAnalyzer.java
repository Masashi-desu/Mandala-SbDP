package io.github.mandala.sbdp.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Captures an actual PostgreSQL schema using read-only information_schema/pg_catalog queries. */
public final class JdbcPostgresSchemaAnalyzer {
    private final Clock clock;

    public JdbcPostgresSchemaAnalyzer() {
        this(Clock.systemUTC());
    }

    public JdbcPostgresSchemaAnalyzer(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PostgresSchemaSnapshot capture(Connection connection) throws SQLException {
        return capture(connection, PostgresCaptureOptions.userSchemas());
    }

    public PostgresSchemaSnapshot capture(Connection connection, PostgresCaptureOptions options) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        String product = connection.getMetaData().getDatabaseProductName();
        if (product == null || !product.toLowerCase().contains("postgresql")) {
            throw new IllegalArgumentException("Expected a PostgreSQL JDBC connection, got: " + product);
        }
        return capture(new JdbcCatalogQueryExecutor(connection), options);
    }

    /** Public conversion seam for saved catalog rows and deterministic unit tests. */
    public PostgresSchemaSnapshot capture(CatalogQueryExecutor executor, PostgresCaptureOptions options)
            throws SQLException {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(options, "options");
        List<String> warnings = new ArrayList<>();
        CatalogRow databaseRow = executor.query(PostgresCatalogQueries.DATABASE).stream().findFirst()
                .orElse(new CatalogRow(Map.of()));
        Map<String, SchemaBuilder> schemas = new LinkedHashMap<>();
        for (CatalogRow row : executor.query(PostgresCatalogQueries.SCHEMAS)) {
            String schemaName = row.string("schema_name");
            if (options.includes(schemaName)) {
                schemas.put(schemaName, new SchemaBuilder(
                        schemaName, row.string("owner_name"), row.string("comment")));
            }
        }
        Map<String, RelationBuilder> relations = new LinkedHashMap<>();
        for (CatalogRow row : executor.query(PostgresCatalogQueries.RELATIONS)) {
            String schemaName = row.string("schema_name");
            if (!options.includes(schemaName)) {
                continue;
            }
            SchemaBuilder schema = schemas.computeIfAbsent(schemaName, SchemaBuilder::new);
            RelationBuilder relation = new RelationBuilder(
                    schemaName,
                    row.string("relation_name"),
                    relationKind(row.string("relation_kind")),
                    row.string("owner_name"),
                    row.string("comment"),
                    row.string("definition"),
                    row.string("parent_schema"),
                    row.string("parent_table"),
                    row.bool("rls_enabled"),
                    row.bool("rls_forced"));
            schema.relations.put(relation.key(), relation);
            relations.put(relation.key(), relation);
        }

        for (CatalogRow row : executor.query(PostgresCatalogQueries.COLUMNS)) {
            RelationBuilder relation = relation(relations, row, options, warnings, "column");
            if (relation != null) {
                relation.columns.add(new ColumnDefinition(
                        row.string("column_name"),
                        row.integer("ordinal"),
                        row.string("formatted_type"),
                        row.string("type_schema"),
                        row.string("type_name"),
                        row.bool("nullable"),
                        row.string("default_expression"),
                        row.string("identity_kind"),
                        row.string("generated_kind"),
                        row.string("comment")));
            }
        }
        for (CatalogRow row : executor.query(PostgresCatalogQueries.CONSTRAINTS)) {
            RelationBuilder relation = relation(relations, row, options, warnings, "constraint");
            if (relation != null) {
                relation.constraints.add(new ConstraintDefinition(
                        row.string("constraint_name"),
                        constraintType(row.string("constraint_type")),
                        row.strings("columns"),
                        row.string("referenced_schema"),
                        row.string("referenced_table"),
                        row.strings("referenced_columns"),
                        row.string("definition"),
                        row.bool("deferrable"),
                        row.bool("initially_deferred")));
            }
        }
        for (CatalogRow row : executor.query(PostgresCatalogQueries.INDEXES)) {
            RelationBuilder relation = relation(relations, row, options, warnings, "index");
            if (relation != null) {
                relation.indexes.add(new IndexDefinition(
                        row.string("index_name"),
                        row.bool("is_unique"),
                        row.bool("is_primary"),
                        row.string("access_method"),
                        row.strings("columns"),
                        row.string("predicate"),
                        row.string("definition")));
            }
        }
        for (CatalogRow row : executor.query(PostgresCatalogQueries.TRIGGERS)) {
            RelationBuilder relation = relation(relations, row, options, warnings, "trigger");
            if (relation != null) {
                relation.triggers.add(new TriggerDefinition(
                        row.string("trigger_name"),
                        row.string("definition"),
                        row.string("function_schema"),
                        row.string("function_name"),
                        row.string("enabled")));
            }
        }
        for (CatalogRow row : executor.query(PostgresCatalogQueries.POLICIES)) {
            RelationBuilder relation = relation(relations, row, options, warnings, "policy");
            if (relation != null) {
                relation.policies.add(new PolicyDefinition(
                        row.string("policy_name"),
                        policyCommand(row.string("command")),
                        row.bool("permissive"),
                        row.strings("roles"),
                        row.string("using_expression"),
                        row.string("check_expression")));
            }
        }

        for (CatalogRow row : executor.query(PostgresCatalogQueries.SEQUENCES)) {
            String schemaName = row.string("schema_name");
            if (options.includes(schemaName)) {
                schemas.computeIfAbsent(schemaName, SchemaBuilder::new).sequences.add(new SequenceDefinition(
                        row.string("sequence_name"),
                        row.string("data_type"),
                        row.longValue("start_value"),
                        row.longValue("minimum_value"),
                        row.longValue("maximum_value"),
                        row.longValue("increment"),
                        row.longValue("cache_size"),
                        row.bool("cycle")));
            }
        }
        Map<String, EnumBuilder> enumBuilders = new LinkedHashMap<>();
        for (CatalogRow row : executor.query(PostgresCatalogQueries.ENUMS)) {
            String schemaName = row.string("schema_name");
            if (options.includes(schemaName)) {
                String key = key(schemaName, row.string("enum_name"));
                enumBuilders.computeIfAbsent(key, ignored -> new EnumBuilder(
                                schemaName, row.string("enum_name"), row.string("comment")))
                        .values.add(row.string("enum_value"));
            }
        }
        enumBuilders.values().forEach(enumBuilder -> schemas.computeIfAbsent(enumBuilder.schema, SchemaBuilder::new)
                .enums.add(enumBuilder.build()));
        for (CatalogRow row : executor.query(PostgresCatalogQueries.DOMAINS)) {
            String schemaName = row.string("schema_name");
            if (options.includes(schemaName)) {
                schemas.computeIfAbsent(schemaName, SchemaBuilder::new).domains.add(new DomainDefinition(
                        row.string("domain_name"),
                        row.string("base_type"),
                        row.bool("not_null"),
                        row.string("default_expression"),
                        row.strings("checks"),
                        row.string("comment")));
            }
        }
        for (CatalogRow row : executor.query(PostgresCatalogQueries.FUNCTIONS)) {
            String schemaName = row.string("schema_name");
            if (options.includes(schemaName)) {
                schemas.computeIfAbsent(schemaName, SchemaBuilder::new).functions.add(new FunctionDefinition(
                        row.string("function_name"),
                        row.string("identity_arguments"),
                        row.string("result_type"),
                        row.string("language"),
                        row.string("function_kind"),
                        row.string("volatility"),
                        row.bool("security_definer"),
                        row.string("definition"),
                        row.string("comment")));
            }
        }

        List<PostgresSchema> immutableSchemas = schemas.values().stream()
                .map(SchemaBuilder::build)
                .sorted(Comparator.comparing(PostgresSchema::name))
                .toList();
        return new PostgresSchemaSnapshot(
                databaseRow.string("database_name"),
                databaseRow.string("server_version"),
                Instant.now(clock),
                immutableSchemas,
                warnings.stream().distinct().toList());
    }

    private RelationBuilder relation(
            Map<String, RelationBuilder> relations,
            CatalogRow row,
            PostgresCaptureOptions options,
            List<String> warnings,
            String item) {
        String schemaName = row.string("schema_name");
        if (!options.includes(schemaName)) {
            return null;
        }
        String relationName = row.string("relation_name");
        RelationBuilder relation = relations.get(key(schemaName, relationName));
        if (relation == null) {
            warnings.add("Catalog returned " + item + " for an unknown relation: " + schemaName + "." + relationName);
        }
        return relation;
    }

    private RelationKind relationKind(String code) {
        return switch (code) {
            case "p" -> RelationKind.PARTITIONED_TABLE;
            case "f" -> RelationKind.FOREIGN_TABLE;
            case "v" -> RelationKind.VIEW;
            case "m" -> RelationKind.MATERIALIZED_VIEW;
            default -> RelationKind.TABLE;
        };
    }

    private ConstraintType constraintType(String code) {
        return switch (code) {
            case "p" -> ConstraintType.PRIMARY_KEY;
            case "f" -> ConstraintType.FOREIGN_KEY;
            case "u" -> ConstraintType.UNIQUE;
            case "c" -> ConstraintType.CHECK;
            case "x" -> ConstraintType.EXCLUSION;
            default -> ConstraintType.UNKNOWN;
        };
    }

    private String policyCommand(String code) {
        return switch (code) {
            case "r" -> "SELECT";
            case "a" -> "INSERT";
            case "w" -> "UPDATE";
            case "d" -> "DELETE";
            default -> "ALL";
        };
    }

    private static String key(String schema, String name) {
        return schema + '\u0000' + name;
    }

    private static final class RelationBuilder {
        private final String schema;
        private final String name;
        private final RelationKind kind;
        private final String owner;
        private final String comment;
        private final String definition;
        private final String parentSchema;
        private final String parentTable;
        private final boolean rowSecurityEnabled;
        private final boolean rowSecurityForced;
        private final List<ColumnDefinition> columns = new ArrayList<>();
        private final List<ConstraintDefinition> constraints = new ArrayList<>();
        private final List<IndexDefinition> indexes = new ArrayList<>();
        private final List<TriggerDefinition> triggers = new ArrayList<>();
        private final List<PolicyDefinition> policies = new ArrayList<>();

        private RelationBuilder(
                String schema,
                String name,
                RelationKind kind,
                String owner,
                String comment,
                String definition,
                String parentSchema,
                String parentTable,
                boolean rowSecurityEnabled,
                boolean rowSecurityForced) {
            this.schema = schema;
            this.name = name;
            this.kind = kind;
            this.owner = owner;
            this.comment = comment;
            this.definition = definition;
            this.parentSchema = parentSchema;
            this.parentTable = parentTable;
            this.rowSecurityEnabled = rowSecurityEnabled;
            this.rowSecurityForced = rowSecurityForced;
        }

        private String key() {
            return JdbcPostgresSchemaAnalyzer.key(schema, name);
        }

        private PostgresRelation build() {
            columns.sort(Comparator.comparingInt(ColumnDefinition::ordinal));
            constraints.sort(Comparator.comparing(ConstraintDefinition::name));
            indexes.sort(Comparator.comparing(IndexDefinition::name));
            triggers.sort(Comparator.comparing(TriggerDefinition::name));
            policies.sort(Comparator.comparing(PolicyDefinition::name));
            return new PostgresRelation(
                    schema,
                    name,
                    kind,
                    owner,
                    comment,
                    definition,
                    parentSchema,
                    parentTable,
                    rowSecurityEnabled,
                    rowSecurityForced,
                    columns,
                    constraints,
                    indexes,
                    triggers,
                    policies);
        }
    }

    private static final class SchemaBuilder {
        private final String name;
        private final String owner;
        private final String comment;
        private final Map<String, RelationBuilder> relations = new LinkedHashMap<>();
        private final List<SequenceDefinition> sequences = new ArrayList<>();
        private final List<EnumDefinition> enums = new ArrayList<>();
        private final List<DomainDefinition> domains = new ArrayList<>();
        private final List<FunctionDefinition> functions = new ArrayList<>();

        private SchemaBuilder(String name) {
            this(name, "", "");
        }

        private SchemaBuilder(String name, String owner, String comment) {
            this.name = name;
            this.owner = owner;
            this.comment = comment;
        }

        private PostgresSchema build() {
            List<PostgresRelation> relationList = relations.values().stream()
                    .map(RelationBuilder::build)
                    .sorted(Comparator.comparing(PostgresRelation::name))
                    .toList();
            sequences.sort(Comparator.comparing(SequenceDefinition::name));
            enums.sort(Comparator.comparing(EnumDefinition::name));
            domains.sort(Comparator.comparing(DomainDefinition::name));
            functions.sort(Comparator.comparing(FunctionDefinition::name)
                    .thenComparing(FunctionDefinition::identityArguments));
            return new PostgresSchema(name, owner, comment, relationList, sequences, enums, domains, functions);
        }
    }

    private static final class EnumBuilder {
        private final String schema;
        private final String name;
        private final String comment;
        private final List<String> values = new ArrayList<>();

        private EnumBuilder(String schema, String name, String comment) {
            this.schema = schema;
            this.name = name;
            this.comment = comment;
        }

        private EnumDefinition build() {
            return new EnumDefinition(name, values, comment);
        }
    }
}
