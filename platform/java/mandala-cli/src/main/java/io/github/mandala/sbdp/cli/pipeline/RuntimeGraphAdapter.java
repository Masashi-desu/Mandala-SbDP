package io.github.mandala.sbdp.cli.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.core.SemanticAttributes;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.opentelemetry.OtlpJsonTraceImporter;
import io.github.mandala.sbdp.opentelemetry.OtlpTraceBatch;
import io.github.mandala.sbdp.opentelemetry.MaskingConfiguration;
import io.github.mandala.sbdp.opentelemetry.RuntimeSpan;
import io.github.mandala.sbdp.opentelemetry.RuntimeTrace;
import io.github.mandala.sbdp.opentelemetry.SensitiveDataMasker;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

final class RuntimeGraphAdapter extends AbstractProjectAdapter {
    private static final Set<String> SAFE_SPAN_ATTRIBUTES = Set.of(
            "http.route", "http.request.method", "http.method", "http.response.status_code",
            "http.status_code", "url.path", "code.namespace", "code.function",
            "db.system", "db.system.name", "db.namespace", "db.operation.name",
            "db.collection.name", "db.query.summary", "db.query.text", "db.statement",
            "messaging.system", "messaging.operation", "messaging.destination.name",
            "rpc.system", "rpc.service", "rpc.method", "error.type", "exception.type"
    );
    private static final Set<String> SAFE_RESOURCE_ATTRIBUTES = Set.of(
            "service.name", "service.namespace", "service.version", "deployment.environment.name",
            "telemetry.sdk.name", "telemetry.sdk.language", "telemetry.sdk.version"
    );
    private static final Set<String> SAFE_EVENT_ATTRIBUTES = Set.of("error.type", "exception.type");
    private static final Pattern UNIX_LOCAL_PATH = Pattern.compile(
            "(?<![A-Za-z0-9])/(?:Users|home|private/var|var/folders|tmp|workspace)/[^\\s,;\\]})]+"
    );
    private static final Pattern WINDOWS_LOCAL_PATH = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])[A-Z]:\\\\(?:Users|Temp|workspace)\\\\[^\\s,;\\]})]+"
    );
    private final ObjectMapper mapper = new ObjectMapper();

    RuntimeGraphAdapter(io.github.mandala.sbdp.cli.RepositoryContext repository) {
        super(repository, Set.of(ChangeCategory.JAVA, ChangeCategory.SQL, ChangeCategory.MIGRATION,
                ChangeCategory.RUNTIME_CAPTURE));
    }
    @Override public String name() { return "opentelemetry"; }

    @Override public DocumentationGraph analyze(RefreshContext context) throws Exception {
        Map<StableId, Node> nodes = new LinkedHashMap<>(); List<Edge> edges = new ArrayList<>(); int batchCount = 0;
        for (String pattern : repository.config().mandala.telemetry.traces) for (Path file : repository.glob(pattern)) {
            for (OtlpTraceBatch batch : readBatches(file)) { batchCount++; for (RuntimeTrace trace : batch.traces()) addTrace(trace, batch.warnings(), file, nodes, edges, context); }
        }
        if (batchCount == 0) throw new IllegalStateException("No importable OTLP JSON traces were found; run `mandala capture-runtime`");
        return persist(GraphSupport.graph(context.projectId(), context.targetCommit(), context.analyzedAt(), nodes.values(), edges));
    }

    /** Maps already-normalized observations; also used by focused adapter tests. */
    DocumentationGraph mapTraces(List<RuntimeTrace> traces, Collection<String> warnings, Path source,
                                 RefreshContext context) {
        Map<StableId, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        traces.forEach(trace -> addTrace(trace, warnings, source, nodes, edges, context));
        return GraphSupport.graph(context.projectId(), context.targetCommit(), context.analyzedAt(),
                nodes.values(), edges);
    }

    private List<OtlpTraceBatch> readBatches(Path file) throws Exception {
        OtlpJsonTraceImporter importer = traceImporter();
        List<OtlpTraceBatch> batches = new ArrayList<>();
        try (InputStream input = traceInput(file);
             MappingIterator<JsonNode> documents = mapper.readerFor(JsonNode.class).readValues(input)) {
            while (documents.hasNextValue()) {
                OtlpTraceBatch batch = importer.importJson(documents.nextValue());
                if (!batch.traces().isEmpty()) batches.add(batch);
            }
        }
        if (batches.isEmpty()) return List.of();

        Map<String, Map<String, RuntimeSpan>> spansByTrace = new TreeMap<>();
        List<String> warnings = new ArrayList<>();
        for (OtlpTraceBatch batch : batches) {
            warnings.addAll(batch.warnings());
            for (RuntimeTrace trace : batch.traces()) {
                Map<String, RuntimeSpan> spans = spansByTrace.computeIfAbsent(trace.traceId(), ignored -> new TreeMap<>());
                for (RuntimeSpan span : trace.spans()) {
                    if (spans.putIfAbsent(span.spanId(), span) != null) {
                        warnings.add("Duplicate span id " + span.spanId() + " in trace " + trace.traceId()
                                + " across OTLP JSON documents");
                    }
                }
            }
        }
        List<RuntimeTrace> traces = spansByTrace.entrySet().stream()
                .map(entry -> new RuntimeTrace(entry.getKey(), entry.getValue().values().stream()
                        .sorted(Comparator.comparing(RuntimeSpan::startTime).thenComparing(RuntimeSpan::spanId))
                        .toList()))
                .toList();
        return List.of(new OtlpTraceBatch(repository.analyzedAt(), traces,
                warnings.stream().distinct().sorted().toList()));
    }

    private InputStream traceInput(Path file) throws Exception {
        BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file));
        input.mark(2);
        int first = input.read();
        int second = input.read();
        input.reset();
        return first == 0x1f && second == 0x8b ? new GZIPInputStream(input) : input;
    }

    private OtlpJsonTraceImporter traceImporter() {
        MaskingConfiguration defaults = MaskingConfiguration.secureDefaults();
        Set<String> exactKeys = new java.util.LinkedHashSet<>(defaults.sensitiveKeys());
        List<String> fragments = new ArrayList<>(defaults.sensitiveKeyFragments());
        for (String configured : repository.config().mandala.security.maskKeys) {
            if (configured == null || configured.isBlank()) continue;
            exactKeys.add(configured.strip());
            fragments.add(configured.strip());
        }
        MaskingConfiguration configuration = new MaskingConfiguration(exactKeys, fragments,
                defaults.replacement(), defaults.maskSqlLiterals());
        return new OtlpJsonTraceImporter(new SensitiveDataMasker(configuration), java.time.Clock.systemUTC());
    }

    private void addTrace(RuntimeTrace trace, Collection<String> warnings, Path file, Map<StableId, Node> nodes,
                          List<Edge> edges, RefreshContext context) {
        String relatedFlow = flowId(trace);
        String semantic = "flow=" + relatedFlow + ";spans="
                + trace.spans().stream().map(this::baseIdentity).sorted().toList();
        StableId traceId = StableId.of("trace:observed:" + GraphSupport.fingerprint(semantic).substring(0, 20));
        String relative = repository.root().relativize(file).toString().replace('\\', '/');
        ElementMetadata metadata = GraphSupport.metadata(EvidenceType.RUNTIME_OBSERVATION, relative,
                "Imported sanitized OTLP trace", name(), context.targetCommit(), context.analyzedAt(), warnings,
                relatedFlow.isBlank() ? List.of() : List.of(relatedFlow), SourceLocation.of(relative));
        nodes.put(traceId, Node.builder(traceId, NodeType.TRACE,
                        "Observed path " + GraphSupport.fingerprint(semantic).substring(0, 8))
                .description("Runtime execution path with " + trace.spans().size() + " spans")
                .metadata(metadata).attributes(GraphSupport.attributes(Map.of(),
                        "traceId", trace.traceId(),
                        "rootSpans", trace.rootSpans().stream().map(RuntimeSpan::name)
                                .map(this::sanitizeLocalPaths).sorted().toList(),
                        "flowId", relatedFlow,
                        "sourceFingerprint", GraphSupport.fingerprint(semantic))).build());

        Map<String, StableId> spans = stableSpanIds(trace, traceId);
        List<RuntimeSpan> ordered = trace.spans().stream().filter(span -> spans.containsKey(span.spanId()))
                .sorted(Comparator.comparing(span -> spans.get(span.spanId()))).toList();
        for (RuntimeSpan span : ordered) {
            StableId spanId = spans.get(span.spanId());
            Map<String, Object> attrs = GraphSupport.attributes(safeSpanAttributes(span.attributes()),
                    "traceId", span.traceId(), "spanId", span.spanId(), "parentSpanId", span.parentSpanId(),
                    "kind", span.kind(), "boundary", span.boundary(), "startTime", span.startTime(),
                    "endTime", span.endTime(), "durationMillis", span.duration().toMillis(),
                    "status", Map.of("code", span.status().code().name()),
                    "resource", safeResourceAttributes(span.resourceAttributes()),
                    "scopeName", sanitizeLocalPaths(span.scopeName()), "scopeVersion", span.scopeVersion(),
                    "events", safeEvents(span), "links", safeLinks(span),
                    "semanticStableId", semanticId(span));
            String displayName = span.name().isBlank() ? span.boundary().name() : sanitizeLocalPaths(span.name());
            nodes.put(spanId, Node.builder(spanId, NodeType.SPAN, displayName)
                    .description(span.boundary() + " runtime span").metadata(metadata).attributes(attrs).build());
            edges.add(GraphSupport.edge(EdgeType.CONTAINS, traceId, spanId, metadata));
        }
        for (RuntimeSpan span : trace.spans()) if (!span.parentSpanId().isBlank() && spans.containsKey(span.parentSpanId()) && spans.containsKey(span.spanId())) edges.add(GraphSupport.edge(EdgeType.CALLS, spans.get(span.parentSpanId()), spans.get(span.spanId()), metadata));
    }

    /**
     * Canonically labels an unordered span forest. Repeated siblings are sorted
     * by semantic content and subtree shape, while raw ids and timing fields are
     * ignored. Consequently changing export order, start times, or trace/span ids
     * cannot swap the durable ids assigned to two same-name observations.
     */
    private Map<String, StableId> stableSpanIds(RuntimeTrace trace, StableId traceId) {
        Map<String, RuntimeSpan> byRawId = new LinkedHashMap<>();
        trace.spans().forEach(span -> byRawId.putIfAbsent(span.spanId(), span));
        Map<String, List<RuntimeSpan>> children = new HashMap<>();
        List<RuntimeSpan> roots = new ArrayList<>();
        for (RuntimeSpan span : byRawId.values()) {
            if (span.parentSpanId().isBlank() || span.parentSpanId().equals(span.spanId())
                    || !byRawId.containsKey(span.parentSpanId())) roots.add(span);
            else children.computeIfAbsent(span.parentSpanId(), ignored -> new ArrayList<>()).add(span);
        }

        Map<String, String> forms = new HashMap<>();
        Map<String, StableId> result = new LinkedHashMap<>();
        assignGroups(roots, "root", traceId, children, forms, new HashSet<>(), result);
        // Malformed cyclic exports have no root. They are still imported without
        // ever using a raw id as a durable-id tie breaker.
        List<RuntimeSpan> remaining = byRawId.values().stream()
                .filter(span -> !result.containsKey(span.spanId())).toList();
        assignGroups(remaining, "detached", traceId, children, forms, new HashSet<>(), result);
        return Map.copyOf(result);
    }

    private void assignGroups(Collection<RuntimeSpan> candidates, String parentPath, StableId traceId,
                              Map<String, List<RuntimeSpan>> children, Map<String, String> forms,
                              Set<String> visiting, Map<String, StableId> result) {
        Map<String, List<RuntimeSpan>> groups = new TreeMap<>();
        candidates.stream().filter(span -> !result.containsKey(span.spanId()))
                .forEach(span -> groups.computeIfAbsent(baseIdentity(span), ignored -> new ArrayList<>()).add(span));
        for (Map.Entry<String, List<RuntimeSpan>> entry : groups.entrySet()) {
            List<RuntimeSpan> ordered = entry.getValue().stream()
                    .sorted(Comparator.comparing(span -> canonicalForm(span, children, forms, visiting)))
                    .toList();
            for (int index = 0; index < ordered.size(); index++) {
                RuntimeSpan span = ordered.get(index);
                String segment = GraphSupport.fingerprint(entry.getKey()).substring(0, 12) + ":" + (index + 1);
                String path = parentPath + "/" + segment;
                StableId id = StableId.of("span:" + traceId.localPart() + ":"
                        + GraphSupport.fingerprint(path).substring(0, 20));
                result.put(span.spanId(), id);
                assignGroups(children.getOrDefault(span.spanId(), List.of()), path, traceId, children, forms,
                        visiting, result);
            }
        }
    }

    private String canonicalForm(RuntimeSpan span, Map<String, List<RuntimeSpan>> children,
                                 Map<String, String> forms, Set<String> visiting) {
        String cached = forms.get(span.spanId());
        if (cached != null) return cached;
        if (!visiting.add(span.spanId())) return GraphSupport.fingerprint(baseIdentity(span), "cycle");
        List<String> descendants = children.getOrDefault(span.spanId(), List.of()).stream()
                .map(child -> canonicalForm(child, children, forms, visiting)).sorted().toList();
        String form = GraphSupport.fingerprint(baseIdentity(span), semanticMaterial(span), descendants);
        visiting.remove(span.spanId());
        forms.put(span.spanId(), form);
        return form;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> semanticMaterial(RuntimeSpan span) {
        Map<String, Object> values = GraphSupport.attributes(safeSpanAttributes(span.attributes()),
                "kind", span.kind(), "boundary", span.boundary(),
                "status", Map.of("code", span.status().code().name()),
                "resource", safeResourceAttributes(span.resourceAttributes()),
                "scopeName", sanitizeLocalPaths(span.scopeName()),
                "scopeVersion", span.scopeVersion(), "events", safeEvents(span), "links", safeLinks(span),
                "semanticStableId", semanticId(span));
        return SemanticAttributes.normalize(values, true);
    }

    private Map<String, Object> safeSpanAttributes(Map<String, Object> attributes) {
        return allowlisted(attributes, SAFE_SPAN_ATTRIBUTES, true);
    }

    private Map<String, Object> safeResourceAttributes(Map<String, Object> attributes) {
        return allowlisted(attributes, SAFE_RESOURCE_ATTRIBUTES, true);
    }

    private Map<String, Object> safeEventAttributes(Map<String, Object> attributes) {
        return allowlisted(attributes, SAFE_EVENT_ATTRIBUTES, true);
    }

    private Map<String, Object> allowlisted(Map<String, Object> attributes, Set<String> exact,
                                            boolean allowMandalaNamespace) {
        Map<String, Object> safe = new TreeMap<>();
        attributes.forEach((key, value) -> {
            String normalized = key.toLowerCase(java.util.Locale.ROOT);
            if (exact.contains(normalized) || allowMandalaNamespace && normalized.startsWith("mandala.")) {
                safe.put(key, sanitizeValue(value));
            }
        });
        return Map.copyOf(safe);
    }

    private List<Map<String, Object>> safeEvents(RuntimeSpan span) {
        return span.events().stream().map(event -> GraphSupport.attributes(Map.of(),
                "name", sanitizeLocalPaths(event.name()), "time", event.time(),
                "attributes", safeEventAttributes(event.attributes()))).toList();
    }

    private List<Map<String, Object>> safeLinks(RuntimeSpan span) {
        return span.links().stream().map(link -> GraphSupport.attributes(Map.of(),
                "traceId", link.traceId(), "spanId", link.spanId(),
                "attributes", allowlisted(link.attributes(), SAFE_SPAN_ATTRIBUTES, true))).toList();
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof String text) return sanitizeLocalPaths(text);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new TreeMap<>();
            map.forEach((key, nested) -> safe.put(String.valueOf(key), sanitizeValue(nested)));
            return Map.copyOf(safe);
        }
        if (value instanceof Collection<?> collection) return collection.stream().map(this::sanitizeValue).toList();
        return value;
    }

    private String sanitizeLocalPaths(String value) {
        String unixSafe = UNIX_LOCAL_PATH.matcher(value == null ? "" : value)
                .replaceAll("[LOCAL_PATH_REDACTED]");
        return WINDOWS_LOCAL_PATH.matcher(unixSafe).replaceAll("[LOCAL_PATH_REDACTED]");
    }

    private String baseIdentity(RuntimeSpan span) {
        return span.boundary() + ":" + semanticId(span) + ":" + span.name();
    }

    private String semanticId(RuntimeSpan span) {
        for (String key : List.of("mandala.stable_id", "mandala.symbol.id", "mandala.endpoint.id", "mandala.dao.id", "mandala.sql.id")) { Object value = span.attributes().get(key); if (value != null && !String.valueOf(value).isBlank()) return sanitizeLocalPaths(String.valueOf(value)); }
        Object namespace = span.attributes().get("code.namespace"); Object function = span.attributes().get("code.function");
        if (namespace != null && function != null) return sanitizeLocalPaths("java:" + namespace + "#" + function);
        return "";
    }
    private String flowId(RuntimeTrace trace) { return trace.spans().stream().map(span -> span.attributes().get("mandala.flow.id")).filter(java.util.Objects::nonNull).map(String::valueOf).map(this::sanitizeLocalPaths).filter(value -> !value.isBlank()).distinct().sorted().findFirst().orElse(""); }
}
