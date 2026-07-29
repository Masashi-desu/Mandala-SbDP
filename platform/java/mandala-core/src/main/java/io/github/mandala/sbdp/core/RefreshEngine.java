package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Coordinates full/incremental collection, reconciliation, validation, stale detection, diffing and caching. */
public final class RefreshEngine {
    private final List<GraphAdapter> adapters;
    private final FileSystemCache cache;
    private final RefreshPlanner planner;
    private final GraphMerger merger;
    private final GraphValidator validator;
    private final GraphDiffer differ;
    private final StaleDetector staleDetector;
    private final ImpactAnalyzer impactAnalyzer;
    private final CustomDocumentationReconciler customDocumentationReconciler;
    private final Clock clock;

    public RefreshEngine(Collection<GraphAdapter> adapters, FileSystemCache cache) {
        this(adapters, cache, new RefreshPlanner(), new GraphMerger(), new GraphValidator(), new GraphDiffer(),
                new StaleDetector(), new ImpactAnalyzer(), Clock.systemUTC());
    }

    public RefreshEngine(Collection<GraphAdapter> adapters, FileSystemCache cache, Clock clock) {
        this(adapters, cache, new RefreshPlanner(), new GraphMerger(), new GraphValidator(),
                new GraphDiffer(clock, new ImpactAnalyzer()), new StaleDetector(), new ImpactAnalyzer(), clock);
    }

    public RefreshEngine(Collection<GraphAdapter> adapters, FileSystemCache cache, RefreshPlanner planner,
                         GraphMerger merger, GraphValidator validator, GraphDiffer differ,
                         StaleDetector staleDetector, ImpactAnalyzer impactAnalyzer, Clock clock) {
        this.adapters = List.copyOf(adapters);
        if (this.adapters.isEmpty()) throw new IllegalArgumentException("At least one GraphAdapter is required");
        Set<String> names = new HashSet<>();
        this.adapters.forEach(adapter -> {
            if (!names.add(adapter.name())) throw new IllegalArgumentException("Duplicate adapter name: " + adapter.name());
        });
        this.cache = Objects.requireNonNull(cache, "cache");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.differ = Objects.requireNonNull(differ, "differ");
        this.staleDetector = Objects.requireNonNull(staleDetector, "staleDetector");
        this.impactAnalyzer = Objects.requireNonNull(impactAnalyzer, "impactAnalyzer");
        this.customDocumentationReconciler = new CustomDocumentationReconciler();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RefreshResult refresh(RefreshRequest request) {
        RefreshPlan initialPlan = planner.plan(request, adapters);
        if (initialPlan.fallback() && !request.fallbackToFull()) {
            throw new RefreshException("Incremental refresh is unsafe and full fallback is disabled: "
                    + String.join("; ", initialPlan.reasons()));
        }
        Instant analyzedAt = Instant.now(clock);
        RefreshContext context = new RefreshContext(request.projectId(), request.targetCommit(),
                request.configurationHash(), request.projectRoot(), analyzedAt, request.configuration());
        List<AdapterRun> runs = new ArrayList<>();
        RefreshPlan actualPlan = initialPlan;
        List<DocumentationGraph> fragments;
        if (initialPlan.executionMode() == RefreshMode.FULL) {
            fragments = runFull(context, request.previousGraph(), runs);
        } else {
            try {
                fragments = runIncremental(context, request, initialPlan, runs);
            } catch (CannotReuseFragment exception) {
                if (!request.fallbackToFull()) throw new RefreshException(exception.getMessage(), exception);
                List<String> reasons = new ArrayList<>(initialPlan.reasons());
                reasons.add(exception.getMessage());
                actualPlan = new RefreshPlan(RefreshMode.INCREMENTAL, RefreshMode.FULL, true,
                        adapters.stream().map(GraphAdapter::name).collect(java.util.stream.Collectors.toSet()), reasons);
                runs.clear();
                fragments = runFull(context, request.previousGraph(), runs);
            }
        }
        MergeResult merged = merger.merge(fragments);
        DocumentationGraph graph = new DocumentationGraph(merged.graph().schemaVersion(), request.projectId(),
                request.targetCommit(), analyzedAt, merged.graph().nodes(), merged.graph().edges());
        CustomDocumentationResult customDocumentation = customDocumentationReconciler.reconcile(graph, analyzedAt);
        graph = customDocumentation.graph();

        Set<StableId> staleIds = Set.of();
        Diff diff = request.previousGraph() == null
                ? differ.diff(DocumentationGraph.empty(request.projectId()), graph)
                : differ.diff(request.previousGraph(), graph);
        if (request.previousGraph() != null && !diff.isEmpty()) {
            StaleResult stale = staleDetector.detectAffectedDocumentation(request.previousGraph(), graph, diff, analyzedAt);
            graph = stale.graph();
            staleIds = stale.staleIds();
            diff = differ.diff(request.previousGraph(), graph);
        }
        ValidationReport validation = validator.validate(graph);
        if (!validation.valid()) {
            throw new RefreshException("Merged Documentation Graph is invalid: " + validation.errors());
        }
        Set<StableId> direct = directChanges(diff);
        ImpactAnalysis impact = impactAnalyzer.analyze(graph, direct, Integer.MAX_VALUE);
        staleIds = allStaleIds(graph);
        List<io.github.mandala.sbdp.model.Conflict> conflicts = allConflicts(graph);
        writeGraphCache(context, graph);
        return new RefreshResult(graph, actualPlan, diff, impact, conflicts, staleIds,
                validation, runs);
    }

    private List<DocumentationGraph> runFull(RefreshContext context, DocumentationGraph previous,
                                             List<AdapterRun> runs) {
        List<DocumentationGraph> fragments = new ArrayList<>();
        for (GraphAdapter adapter : adapters) {
            Instant start = Instant.now(clock);
            DocumentationGraph fragment = invokeFull(adapter, context);
            fragment = normalizeFragment(fragment, adapter, context);
            cacheAdapterFragment(adapter, context, fragment);
            fragments.add(fragment);
            runs.add(new AdapterRun(adapter.name(), adapter.version(), AdapterRunStatus.FULL,
                    Duration.between(start, Instant.now(clock))));
        }
        DocumentationGraph retained = retainedDocumentation(previous, context, fragments);
        if (!retained.nodes().isEmpty() || !retained.edges().isEmpty()) fragments.add(retained);
        return fragments;
    }

    private List<DocumentationGraph> runIncremental(RefreshContext context, RefreshRequest request,
                                                    RefreshPlan plan, List<AdapterRun> runs) {
        if (request.changes().files().isEmpty()) {
            List<DocumentationGraph> promoted = new ArrayList<>();
            for (GraphAdapter adapter : adapters) {
                DocumentationGraph fragment = cachedAdapterFragment(adapter, context,
                        request.previousGraph().targetCommit()).orElseThrow(() -> new CannotReuseFragment(
                        "No commit-matching cached fragment is available for adapter " + adapter.name()));
                fragment = promoteFragment(fragment, context);
                cacheAdapterFragment(adapter, context, fragment);
                promoted.add(fragment);
                runs.add(new AdapterRun(adapter.name(), adapter.version(), AdapterRunStatus.CACHE_REUSED,
                        Duration.ZERO));
            }
            DocumentationGraph retained = retainedDocumentation(request.previousGraph(), context, promoted);
            if (!retained.nodes().isEmpty()) promoted.add(retained);
            return promoted;
        }
        List<DocumentationGraph> fragments = new ArrayList<>();
        for (GraphAdapter adapter : adapters) {
            Instant start = Instant.now(clock);
            java.util.Optional<DocumentationGraph> cached = cachedAdapterFragment(adapter, context,
                    request.previousGraph().targetCommit());
            DocumentationGraph previousFragment = cached.orElse(DocumentationGraph.empty(context.projectId()));
            boolean previousAvailable = cached.isPresent();
            if (plan.affectedAdapters().contains(adapter.name())) {
                if (!previousAvailable) {
                    throw new CannotReuseFragment("No previous fragment is available for affected adapter " + adapter.name());
                }
                DocumentationGraph fragment;
                try {
                    fragment = adapter.analyzeIncremental(context, previousFragment, request.changes());
                } catch (Exception exception) {
                    throw new RefreshException("Incremental adapter failed: " + adapter.name(), exception);
                }
                fragment = normalizeFragment(fragment, adapter, context);
                cacheAdapterFragment(adapter, context, fragment);
                fragments.add(fragment);
                runs.add(new AdapterRun(adapter.name(), adapter.version(), AdapterRunStatus.INCREMENTAL,
                        Duration.between(start, Instant.now(clock))));
            } else {
                if (!previousAvailable) {
                    throw new CannotReuseFragment("No commit-matching cached fragment is available for adapter " + adapter.name());
                }
                previousFragment = promoteFragment(previousFragment, context);
                fragments.add(previousFragment);
                cacheAdapterFragment(adapter, context, previousFragment);
                AdapterRunStatus status = cached.isPresent()
                        ? AdapterRunStatus.CACHE_REUSED : AdapterRunStatus.PREVIOUS_GRAPH_REUSED;
                runs.add(new AdapterRun(adapter.name(), adapter.version(), status,
                        Duration.between(start, Instant.now(clock))));
            }
        }
        DocumentationGraph retained = retainedDocumentation(request.previousGraph(), context, fragments);
        if (!retained.nodes().isEmpty() || !retained.edges().isEmpty()) fragments.add(retained);
        return fragments;
    }

    private DocumentationGraph invokeFull(GraphAdapter adapter, RefreshContext context) {
        try {
            return adapter.analyze(context);
        } catch (Exception exception) {
            throw new RefreshException("Full adapter failed: " + adapter.name(), exception);
        }
    }

    private DocumentationGraph normalizeFragment(DocumentationGraph fragment, GraphAdapter adapter,
                                                 RefreshContext context) {
        if (!fragment.projectId().equals(context.projectId())) {
            throw new RefreshException("Adapter " + adapter.name() + " returned project " + fragment.projectId()
                    + " instead of " + context.projectId());
        }
        List<Node> nodes = fragment.nodes().stream().map(node -> node.toBuilder()
                .metadata(attribute(node.metadata(), adapter, context)).build()).toList();
        List<Edge> edges = fragment.edges().stream().map(edge -> edge.toBuilder()
                .metadata(attribute(edge.metadata(), adapter, context)).build()).toList();
        return new DocumentationGraph(fragment.schemaVersion(), context.projectId(), context.targetCommit(),
                context.analyzedAt(), nodes, edges);
    }

    private DocumentationGraph promoteFragment(DocumentationGraph fragment, RefreshContext context) {
        return new DocumentationGraph(fragment.schemaVersion(), context.projectId(), context.targetCommit(),
                context.analyzedAt(), fragment.nodes(), fragment.edges());
    }

    private ElementMetadata attribute(ElementMetadata metadata, GraphAdapter adapter, RefreshContext context) {
        String adapterName = metadata.adapter().isBlank() ? adapter.name() : metadata.adapter();
        String commit = metadata.targetCommit().isBlank() ? context.targetCommit() : metadata.targetCommit();
        Instant time = metadata.analyzedAt() == null ? context.analyzedAt() : metadata.analyzedAt();
        return new ElementMetadata(metadata.evidence(), metadata.sourceLocations(), commit, time, adapterName,
                metadata.confidence(), metadata.reviewState(), metadata.stale(), metadata.conflicts(),
                metadata.warnings(), metadata.relatedTraces(), metadata.relatedScenarios());
    }

    private DocumentationGraph retainedDocumentation(DocumentationGraph previous, RefreshContext context,
                                                      Collection<DocumentationGraph> currentFragments) {
        if (previous == null) return DocumentationGraph.empty(context.projectId());
        Set<StableId> currentIds = currentFragments.stream().flatMap(fragment -> fragment.nodes().stream())
                .map(Node::id).collect(java.util.stream.Collectors.toSet());
        Set<String> authoritativeCustomAdapters = adapters.stream()
                .filter(adapter -> adapter.changeCategories().contains(ChangeCategory.CUSTOM_HTML))
                .map(GraphAdapter::name).collect(java.util.stream.Collectors.toSet());
        List<Node> retained = new ArrayList<>();
        for (Node node : previous.nodes()) {
            if (node.type() == NodeType.CUSTOM_HTML_SECTION) {
                Set<String> nodeAdapters = node.metadata().adapter().isBlank() ? Set.of()
                        : java.util.Arrays.stream(node.metadata().adapter().split(","))
                        .map(String::strip).collect(java.util.stream.Collectors.toSet());
                if (currentIds.contains(node.id())
                        || nodeAdapters.stream().anyMatch(authoritativeCustomAdapters::contains)) continue;
                Map<String, Object> attributes = new java.util.TreeMap<>(node.attributes());
                Set<String> references = new TreeSet<>();
                Object existing = attributes.get("references");
                if (existing instanceof Collection<?> values) values.forEach(value -> references.add(String.valueOf(value)));
                else if (existing != null) references.add(String.valueOf(existing));
                previous.edges().stream().filter(edge -> edge.from().equals(node.id()) || edge.to().equals(node.id()))
                        .map(edge -> edge.from().equals(node.id()) ? edge.to() : edge.from())
                        .map(StableId::value).forEach(references::add);
                if (!references.isEmpty()) attributes.put("references", List.copyOf(references));
                retained.add(node.toBuilder().attributes(attributes).build());
                continue;
            }
            List<io.github.mandala.sbdp.model.Evidence> humanEvidence = node.metadata().evidence().stream()
                    .filter(evidence -> evidence.type() == EvidenceType.HUMAN_INPUT).toList();
            if (humanEvidence.isEmpty()) continue;
            StableId retainedId = StableId.of("custom-retained:" + StableIdGenerator.digest(node.id().value()));
            if (currentIds.contains(retainedId)) continue;
            String description = humanEvidence.stream().map(io.github.mandala.sbdp.model.Evidence::description)
                    .filter(value -> !value.isBlank()).max(java.util.Comparator.comparingInt(String::length))
                    .orElse(node.description());
            ElementMetadata metadata = new ElementMetadata(humanEvidence, node.metadata().sourceLocations(),
                    node.metadata().targetCommit(), node.metadata().analyzedAt(), "retained-human",
                    Confidence.HUMAN_REVIEWED, node.metadata().reviewState(), node.metadata().stale(),
                    node.metadata().conflicts(), node.metadata().warnings(), node.metadata().relatedTraces(),
                    node.metadata().relatedScenarios());
            retained.add(Node.builder(retainedId, NodeType.CUSTOM_HTML_SECTION,
                            "Review for " + node.displayName())
                    .description(description).metadata(metadata)
                    .attributes(Map.of("references", List.of(node.id().value()),
                            "sourceNodeId", node.id().value(), "sourceNodeType", node.type().name()))
                    .build());
        }
        return DocumentationGraph.of(context.projectId(), previous.targetCommit(), previous.analyzedAt(), retained,
                List.of());
    }

    private Set<StableId> allStaleIds(DocumentationGraph graph) {
        Set<StableId> result = new TreeSet<>();
        graph.nodes().stream().filter(node -> node.metadata().stale().stale()).map(Node::id).forEach(result::add);
        graph.edges().stream().filter(edge -> edge.metadata().stale().stale()).map(Edge::id).forEach(result::add);
        return result;
    }

    private List<io.github.mandala.sbdp.model.Conflict> allConflicts(DocumentationGraph graph) {
        java.util.TreeMap<StableId, io.github.mandala.sbdp.model.Conflict> result = new java.util.TreeMap<>();
        java.util.stream.Stream.concat(
                        graph.nodes().stream().flatMap(node -> node.metadata().conflicts().stream()),
                        graph.edges().stream().flatMap(edge -> edge.metadata().conflicts().stream()))
                .forEach(conflict -> result.put(conflict.id(), conflict));
        return List.copyOf(result.values());
    }

    private void cacheAdapterFragment(GraphAdapter adapter, RefreshContext context, DocumentationGraph graph) {
        try {
            cache.putGraph(adapterDescriptor(context, adapter), graph, context.targetCommit(),
                    context.configurationHash(), adapter.name(), adapter.version());
        } catch (IOException exception) {
            throw new RefreshException("Cannot persist adapter cache for " + adapter.name(), exception);
        }
    }

    private java.util.Optional<DocumentationGraph> cachedAdapterFragment(GraphAdapter adapter, RefreshContext context,
                                                                          String previousCommit) {
        return cache.getGraph(adapterDescriptor(context, adapter), new CacheRequirements(previousCommit,
                context.configurationHash(), adapter.name(), adapter.version()));
    }

    private CacheDescriptor adapterDescriptor(RefreshContext context, GraphAdapter adapter) {
        return new CacheDescriptor(CacheKind.ADAPTER_RESULT, context.projectId(), adapter.name());
    }

    private void writeGraphCache(RefreshContext context, DocumentationGraph graph) {
        try {
            cache.putGraph(new CacheDescriptor(CacheKind.DOCUMENTATION_GRAPH, context.projectId(), "latest"), graph,
                    context.targetCommit(), context.configurationHash(), "mandala-core",
                    FileSystemCache.GRAPH_CODEC_VERSION);
        } catch (IOException exception) {
            throw new RefreshException("Cannot persist Documentation Graph cache", exception);
        }
    }

    private Set<StableId> directChanges(Diff diff) {
        Set<StableId> ids = new TreeSet<>();
        diff.addedNodes().forEach(node -> ids.add(node.id()));
        diff.removedNodes().forEach(node -> ids.add(node.id()));
        diff.modifiedNodes().forEach(change -> ids.add(change.id()));
        diff.addedEdges().forEach(edge -> { ids.add(edge.from()); ids.add(edge.to()); });
        diff.removedEdges().forEach(edge -> { ids.add(edge.from()); ids.add(edge.to()); });
        diff.modifiedEdges().forEach(change -> {
            ids.add(change.before().from());
            ids.add(change.before().to());
            ids.add(change.after().from());
            ids.add(change.after().to());
        });
        return ids;
    }

    private static final class CannotReuseFragment extends RuntimeException {
        private CannotReuseFragment(String message) { super(message); }
    }
}
