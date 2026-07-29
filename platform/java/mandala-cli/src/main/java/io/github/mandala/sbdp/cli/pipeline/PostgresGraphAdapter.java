package io.github.mandala.sbdp.cli.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.postgres.ConstraintType;
import io.github.mandala.sbdp.postgres.FunctionDefinition;
import io.github.mandala.sbdp.postgres.JdbcPostgresSchemaAnalyzer;
import io.github.mandala.sbdp.postgres.PostgresCaptureOptions;
import io.github.mandala.sbdp.postgres.PostgresRelation;
import io.github.mandala.sbdp.postgres.PostgresSchemaSnapshot;
import io.github.mandala.sbdp.postgres.RelationKind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PostgresGraphAdapter extends AbstractProjectAdapter {
    private final boolean connect;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    PostgresGraphAdapter(io.github.mandala.sbdp.cli.RepositoryContext repository, boolean connect) {
        super(repository, Set.of(ChangeCategory.MIGRATION, ChangeCategory.SQL,
                ChangeCategory.DATABASE_CAPTURE)); this.connect = connect;
    }
    @Override public String name() { return "postgres"; }

    @Override public DocumentationGraph analyze(RefreshContext context) throws Exception {
        PostgresSchemaSnapshot snapshot = connect ? capture() : load();
        Map<StableId, Node> nodes = new LinkedHashMap<>(); List<Edge> edges = new ArrayList<>();
        ElementMetadata metadata = GraphSupport.metadata(EvidenceType.DATABASE_INTROSPECTION,
                repository.config().mandala.database.snapshot, "PostgreSQL " + snapshot.serverVersion() + " catalog introspection",
                name(), context.targetCommit(), context.analyzedAt(), snapshot.warnings(), List.of(),
                SourceLocation.of(repository.config().mandala.database.snapshot));
        for (var schema : snapshot.schemas()) {
            StableId schemaId = StableId.of("schema:" + schema.name());
            nodes.put(schemaId, Node.builder(schemaId, NodeType.DB_SCHEMA, schema.name()).description(schema.comment())
                    .metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "owner", schema.owner(), "sequences", schema.sequences(), "enums", schema.enums(), "domains", schema.domains(), "sourceFingerprint", GraphSupport.fingerprint(schema))).build());
            for (FunctionDefinition function : schema.functions()) {
                StableId functionId = StableId.of("function:" + schema.name() + "." + function.name() + "(" + compact(function.identityArguments()) + ")");
                nodes.put(functionId, Node.builder(functionId, NodeType.DB_FUNCTION, schema.name() + "." + function.name()).description(function.comment()).metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "schema", schema.name(), "identityArguments", function.identityArguments(), "resultType", function.resultType(), "language", function.language(), "kind", function.kind(), "volatility", function.volatility(), "securityDefiner", function.securityDefiner(), "definition", function.definition())).build());
                edges.add(GraphSupport.edge(EdgeType.CONTAINS, schemaId, functionId, metadata));
            }
            for (PostgresRelation relation : schema.relations()) {
                if (repository.config().mandala.database.excludeTables.contains(relation.name())) continue;
                StableId relationId = GraphSupport.IDS.table(relation.schema(), relation.name());
                NodeType type = switch (relation.kind()) { case VIEW -> NodeType.DB_VIEW; case MATERIALIZED_VIEW -> NodeType.DB_MATERIALIZED_VIEW; default -> NodeType.DB_TABLE; };
                Map<String, Object> attrs = GraphSupport.attributes(Map.of(), "schema", relation.schema(), "table", relation.name(), "kind", relation.kind(), "owner", relation.owner(), "definition", relation.definition(), "parentSchema", relation.parentSchema(), "parentTable", relation.parentTable(), "rowSecurityEnabled", relation.rowSecurityEnabled(), "rowSecurityForced", relation.rowSecurityForced(), "constraints", relation.constraints(), "indexes", relation.indexes(), "sourceFingerprint", GraphSupport.fingerprint(relation));
                nodes.put(relationId, Node.builder(relationId, type, relation.qualifiedName()).description(relation.comment()).metadata(metadata).attributes(attrs).build());
                edges.add(GraphSupport.edge(EdgeType.CONTAINS, schemaId, relationId, metadata));
                for (var column : relation.columns()) {
                    StableId columnId = GraphSupport.IDS.column(relation.schema(), relation.name(), column.name());
                    Map<String, Object> columnAttrs = GraphSupport.attributes(Map.of(), "schema", relation.schema(), "table", relation.name(), "column", column.name(), "ordinal", column.ordinal(), "type", column.formattedType(), "typeSchema", column.typeSchema(), "typeName", column.typeName(), "nullable", column.nullable(), "default", column.defaultExpression(), "identity", column.identityKind(), "generated", column.generatedKind(), "sourceFingerprint", GraphSupport.fingerprint(column));
                    nodes.put(columnId, Node.builder(columnId, NodeType.DB_COLUMN, relation.qualifiedName() + "." + column.name()).description(column.comment()).metadata(metadata).attributes(columnAttrs).build());
                    edges.add(GraphSupport.edge(EdgeType.CONTAINS, relationId, columnId, metadata));
                }
                for (var constraint : relation.constraints()) if (constraint.type() == ConstraintType.FOREIGN_KEY && !constraint.referencedTable().isBlank()) {
                    String referencedSchema = constraint.referencedSchema().isBlank() ? relation.schema() : constraint.referencedSchema();
                    StableId target = GraphSupport.IDS.table(referencedSchema, constraint.referencedTable());
                    nodes.putIfAbsent(target, Node.builder(target, NodeType.DB_TABLE, referencedSchema + "." + constraint.referencedTable()).metadata(metadata).attributes(Map.of("schema", referencedSchema, "table", constraint.referencedTable())).build());
                    edges.add(GraphSupport.edge(EdgeType.FK_TO, relationId, target, metadata));
                }
                for (var trigger : relation.triggers()) {
                    StableId triggerId = StableId.of("trigger:" + relation.qualifiedName() + "." + trigger.name());
                    nodes.put(triggerId, Node.builder(triggerId, NodeType.DB_TRIGGER, trigger.name()).description(trigger.definition()).metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "table", relation.qualifiedName(), "functionSchema", trigger.functionSchema(), "functionName", trigger.functionName(), "enabled", trigger.enabled())).build());
                    edges.add(GraphSupport.edge(EdgeType.FIRES_TRIGGER, relationId, triggerId, metadata));
                    nodes.values().stream().filter(node -> node.type() == NodeType.DB_FUNCTION && node.id().value().startsWith("function:" + trigger.functionSchema() + "." + trigger.functionName() + "(")).findFirst().ifPresent(function -> edges.add(GraphSupport.edge(EdgeType.CALLS_FUNCTION, triggerId, function.id(), metadata)));
                }
                for (var policy : relation.policies()) {
                    StableId policyId = StableId.of("policy:" + relation.qualifiedName() + "." + policy.name());
                    nodes.put(policyId, Node.builder(policyId, NodeType.DB_POLICY, policy.name()).description(policy.command() + " policy on " + relation.qualifiedName()).metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "command", policy.command(), "permissive", policy.permissive(), "roles", policy.roles(), "using", policy.usingExpression(), "check", policy.checkExpression())).build());
                    edges.add(GraphSupport.edge(EdgeType.CONTAINS, relationId, policyId, metadata));
                }
            }
        }
        return persist(GraphSupport.graph(context.projectId(), context.targetCommit(), context.analyzedAt(), nodes.values(), edges));
    }

    private PostgresSchemaSnapshot capture() throws Exception {
        var config = repository.config().mandala.database; String username = requireEnv(config.connection.usernameEnv); String password = requireEnv(config.connection.passwordEnv);
        try (Connection connection = DriverManager.getConnection(config.connection.url, username, password)) {
            connection.setReadOnly(true);
            PostgresSchemaSnapshot snapshot = new JdbcPostgresSchemaAnalyzer().capture(connection, new PostgresCaptureOptions(new LinkedHashSet<>(config.schemas), Set.of("pg_catalog", "information_schema")));
            Path target = repository.resolve(config.snapshot); Files.createDirectories(target.getParent()); mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), snapshot); return snapshot;
        }
    }

    private PostgresSchemaSnapshot load() throws Exception {
        Path target = repository.resolve(repository.config().mandala.database.snapshot);
        if (!Files.isRegularFile(target)) throw new IllegalStateException("PostgreSQL snapshot is missing; start the local database and run `mandala analyze-db`: " + target);
        return mapper.readValue(target.toFile(), PostgresSchemaSnapshot.class);
    }

    private String requireEnv(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException("Required database environment variable is not set: " + name); return value; }
    private String compact(String value) { return value.replaceAll("\\s+", "").replace(':', '_'); }
}
