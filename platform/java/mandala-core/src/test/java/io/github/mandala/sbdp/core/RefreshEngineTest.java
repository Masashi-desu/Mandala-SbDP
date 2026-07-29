package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceScope;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshEngineTest {
    @TempDir
    Path temp;

    @Test
    void usesTheProvidedClockForDeterministicRefreshMetadata() {
        Instant fixed = Instant.parse("2025-01-01T00:00:00Z");
        Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);
        RefreshEngine engine = new RefreshEngine(List.of(new MutableAdapter(true)),
                new FileSystemCache(temp.resolve("cache"), clock), clock);

        RefreshResult result = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));

        assertEquals(fixed, result.graph().analyzedAt());
        assertEquals(fixed, result.diff().createdAt());
    }

    @Test
    void executesFullThenIncrementalRefreshAndProducesDiffImpactAndCache() {
        MutableAdapter adapter = new MutableAdapter(true);
        RefreshEngine engine = engine(adapter);
        RefreshResult full = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));
        adapter.status = 201;
        RefreshRequest incremental = new RefreshRequest("sample", "c2", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("src/ProjectController.java")), true,
                full.graph(), Map.of());

        RefreshResult refreshed = engine.refresh(incremental);

        assertEquals(1, adapter.fullCalls.get());
        assertEquals(1, adapter.incrementalCalls.get());
        assertEquals(RefreshMode.INCREMENTAL, refreshed.plan().executionMode());
        assertFalse(refreshed.diff().isEmpty());
        assertTrue(refreshed.impact().impactedFlows().contains(StableId.of("flow:create")));
        assertEquals(201L, refreshed.graph().node(StableId.of("endpoint:POST:/projects"))
                .orElseThrow().attributes().get("status"));
        assertTrue(new FileSystemCache(temp.resolve("cache")).getGraph(
                new CacheDescriptor(CacheKind.DOCUMENTATION_GRAPH, "sample", "latest"),
                new CacheRequirements("c2", "cfg", "mandala-core", FileSystemCache.GRAPH_CODEC_VERSION)).isPresent());
    }

    @Test
    void fallsBackToFullWhenAdapterCannotRunIncrementally() {
        MutableAdapter adapter = new MutableAdapter(false);
        RefreshEngine engine = engine(adapter);
        RefreshResult full = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));
        adapter.status = 201;

        RefreshResult refreshed = engine.refresh(new RefreshRequest("sample", "c2", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("src/A.java")), true, full.graph(), Map.of()));

        assertTrue(refreshed.plan().fallback());
        assertEquals(RefreshMode.FULL, refreshed.plan().executionMode());
        assertEquals(2, adapter.fullCalls.get());
        assertEquals(0, adapter.incrementalCalls.get());
    }

    @Test
    void refusesUnsafeIncrementalWhenFallbackIsDisabled() {
        MutableAdapter adapter = new MutableAdapter(false);
        RefreshEngine engine = engine(adapter);
        RefreshResult full = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));

        RefreshRequest request = new RefreshRequest("sample", "c2", "cfg", temp, RefreshMode.INCREMENTAL,
                ChangeSet.ofPaths(List.of("mandala.yml")), false, full.graph(), Map.of());

        assertThrows(RefreshException.class, () -> engine.refresh(request));
    }

    @Test
    void retainsHumanDocumentationAndMarksItStaleAfterImplementationChanges() {
        MutableAdapter adapter = new MutableAdapter(true);
        RefreshEngine engine = engine(adapter);
        RefreshResult full = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));
        Node human = Node.builder(StableId.of("custom:flow-create"), NodeType.CUSTOM_HTML_SECTION, "Explanation")
                .description("Approved behavior")
                .metadata(ElementMetadata.builder().evidence(List.of(Evidence.humanReviewed("custom.html", "intent")))
                        .confidence(Confidence.HUMAN_REVIEWED).build()).build();
        Edge documents = Edge.of("edge:documents:1", EdgeType.DOCUMENTED_BY, StableId.of("flow:create"), human.id());
        DocumentationGraph reviewed = DocumentationGraph.of("sample", "c1", Instant.EPOCH,
                java.util.stream.Stream.concat(full.graph().nodes().stream(), java.util.stream.Stream.of(human)).toList(),
                java.util.stream.Stream.concat(full.graph().edges().stream(), java.util.stream.Stream.of(documents)).toList());
        adapter.status = 201;

        RefreshResult refreshed = engine.refresh(new RefreshRequest("sample", "c2", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("src/A.java")), true, reviewed, Map.of()));

        Node retained = refreshed.graph().node(human.id()).orElseThrow();
        assertTrue(retained.metadata().stale().stale());
        assertTrue(refreshed.staleIds().contains(human.id()));
    }

    @Test
    void doesNotKeepDeletedImplementationNodeMerelyBecauseItHadHumanEvidence() {
        MutableAdapter adapter = new MutableAdapter(true);
        RefreshEngine engine = engine(adapter);
        RefreshResult full = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));
        StableId endpointId = StableId.of("endpoint:POST:/projects");
        Node endpoint = full.graph().node(endpointId).orElseThrow();
        ElementMetadata reviewedMetadata = ElementMetadata.builder()
                .evidence(java.util.stream.Stream.concat(endpoint.metadata().evidence().stream(),
                        java.util.stream.Stream.of(Evidence.humanReviewed("review.md", "Projects must be audited")))
                        .toList())
                .confidence(Confidence.HUMAN_REVIEWED).build();
        Node reviewedEndpoint = endpoint.toBuilder().description("Projects must be audited")
                .metadata(reviewedMetadata).build();
        DocumentationGraph reviewed = DocumentationGraph.of("sample", "c1", Instant.EPOCH,
                full.graph().nodes().stream().map(node -> node.id().equals(endpointId) ? reviewedEndpoint : node).toList(),
                full.graph().edges());
        adapter.includeEndpoint = false;

        RefreshResult refreshed = engine.refresh(new RefreshRequest("sample", "c2", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("src/A.java")), true, reviewed, Map.of()));

        assertTrue(refreshed.graph().node(endpointId).isEmpty());
        Node retainedReview = refreshed.graph().nodes().stream()
                .filter(node -> node.id().namespace().equals("custom-retained")).findFirst().orElseThrow();
        assertTrue(retainedReview.metadata().stale().stale());
        assertTrue(retainedReview.metadata().conflicts().stream()
                .anyMatch(conflict -> conflict.description().contains("missing")));
        assertTrue(refreshed.diff().removedNodes().stream().anyMatch(node -> node.id().equals(endpointId)));
    }

    @Test
    void promotesUnaffectedAdapterCacheAcrossConsecutiveIncrementalCommits() {
        MutableAdapter java = new MutableAdapter(true);
        StaticAdapter sql = new StaticAdapter();
        RefreshEngine engine = engine(java, sql);
        RefreshResult c1 = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));
        java.status = 201;
        RefreshResult c2 = engine.refresh(new RefreshRequest("sample", "c2", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("src/A.java")), true, c1.graph(), Map.of()));
        java.status = 202;

        RefreshResult c3 = engine.refresh(new RefreshRequest("sample", "c3", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("src/B.java")), true, c2.graph(), Map.of()));

        assertEquals(RefreshMode.INCREMENTAL, c2.plan().executionMode());
        assertEquals(RefreshMode.INCREMENTAL, c3.plan().executionMode());
        assertEquals(1, sql.fullCalls.get());
        assertEquals(0, sql.incrementalCalls.get());
        assertEquals("c3", c3.graph().targetCommit());
    }

    @Test
    void rejectsPreviousGraphFromAnotherProject() {
        DocumentationGraph other = DocumentationGraph.empty("other");
        assertThrows(IllegalArgumentException.class, () -> new RefreshRequest("sample", "c", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.empty(), true, other, Map.of()));
    }

    @Test
    void authoritativeCustomAdapterCanEditAndDeleteCustomSectionWithoutOldValueReturning() {
        MutableAdapter java = new MutableAdapter(true);
        MutableCustomAdapter custom = new MutableCustomAdapter();
        RefreshEngine engine = engine(java, custom);
        RefreshResult c1 = engine.refresh(RefreshRequest.full("sample", "c1", "cfg", temp));
        custom.description = "Edited current explanation";

        RefreshResult c2 = engine.refresh(new RefreshRequest("sample", "c2", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("mandala/custom/create/details.html")), true,
                c1.graph(), Map.of()));

        Node edited = c2.graph().node(StableId.of("custom:create-details")).orElseThrow();
        assertEquals("Edited current explanation", edited.description());
        assertFalse(edited.metadata().conflicted());
        custom.present = false;
        RefreshResult c3 = engine.refresh(new RefreshRequest("sample", "c3", "cfg", temp,
                RefreshMode.INCREMENTAL, ChangeSet.ofPaths(List.of("mandala/custom/create/details.html")), true,
                c2.graph(), Map.of()));
        assertTrue(c3.graph().node(edited.id()).isEmpty());
    }

    private RefreshEngine engine(GraphAdapter... adapters) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);
        return new RefreshEngine(List.of(adapters), new FileSystemCache(temp.resolve("cache"), clock),
                new RefreshPlanner(), new GraphMerger(), new GraphValidator(),
                new GraphDiffer(clock, new ImpactAnalyzer()), new StaleDetector(), new ImpactAnalyzer(), clock);
    }

    private static final class MutableAdapter implements GraphAdapter {
        private final boolean incremental;
        private int status = 200;
        private boolean includeEndpoint = true;
        private final AtomicInteger fullCalls = new AtomicInteger();
        private final AtomicInteger incrementalCalls = new AtomicInteger();

        private MutableAdapter(boolean incremental) {
            this.incremental = incremental;
        }

        public String name() { return "java"; }
        public String version() { return "1"; }
        public Set<ChangeCategory> changeCategories() { return Set.of(ChangeCategory.JAVA); }
        public boolean supportsIncremental() { return incremental; }

        public DocumentationGraph analyze(RefreshContext context) {
            fullCalls.incrementAndGet();
            return graph(context);
        }

        public DocumentationGraph analyzeIncremental(RefreshContext context, DocumentationGraph previous,
                                                     ChangeSet changes) {
            incrementalCalls.incrementAndGet();
            return graph(context);
        }

        private DocumentationGraph graph(RefreshContext context) {
            Evidence evidence = new Evidence(null, EvidenceType.SOURCE_CODE, EvidenceScope.TECHNICAL_FACT,
                    "ProjectController.java", "handler", null, context.targetCommit(), context.analyzedAt(),
                    name(), Confidence.INFERRED, Map.of());
            ElementMetadata metadata = ElementMetadata.builder().evidence(List.of(evidence))
                    .confidence(Confidence.INFERRED).build();
            Node flow = Node.builder(StableId.of("flow:create"), NodeType.E2E_FLOW, "Create")
                    .metadata(metadata).build();
            Node endpoint = Node.builder(StableId.of("endpoint:POST:/projects"), NodeType.HTTP_ENDPOINT, "Create")
                    .metadata(metadata).attributes(Map.of("status", status)).build();
            Edge edge = Edge.builder(StableId.of("edge:calls:1"), EdgeType.CALLS_HTTP, flow.id(), endpoint.id())
                    .metadata(metadata).build();
            return DocumentationGraph.of(context.projectId(), context.targetCommit(), context.analyzedAt(),
                    includeEndpoint ? List.of(flow, endpoint) : List.of(flow),
                    includeEndpoint ? List.of(edge) : List.of());
        }
    }

    private static final class StaticAdapter implements GraphAdapter {
        private final AtomicInteger fullCalls = new AtomicInteger();
        private final AtomicInteger incrementalCalls = new AtomicInteger();

        public String name() { return "sql"; }
        public String version() { return "1"; }
        public Set<ChangeCategory> changeCategories() { return Set.of(ChangeCategory.SQL); }
        public boolean supportsIncremental() { return true; }
        public DocumentationGraph analyze(RefreshContext context) {
            fullCalls.incrementAndGet();
            return DocumentationGraph.of(context.projectId(), context.targetCommit(), context.analyzedAt(),
                    List.of(Node.of("sql:ProjectDao/select.sql", NodeType.SQL_STATEMENT, "select projects")),
                    List.of());
        }
        public DocumentationGraph analyzeIncremental(RefreshContext context, DocumentationGraph previous,
                                                     ChangeSet changes) {
            incrementalCalls.incrementAndGet();
            return analyze(context);
        }
    }

    private static final class MutableCustomAdapter implements GraphAdapter {
        private String description = "Initial explanation";
        private boolean present = true;

        public String name() { return "custom-html"; }
        public String version() { return "1"; }
        public Set<ChangeCategory> changeCategories() { return Set.of(ChangeCategory.CUSTOM_HTML); }
        public boolean supportsIncremental() { return true; }
        public DocumentationGraph analyze(RefreshContext context) { return graph(context); }
        public DocumentationGraph analyzeIncremental(RefreshContext context, DocumentationGraph previous,
                                                     ChangeSet changes) { return graph(context); }
        private DocumentationGraph graph(RefreshContext context) {
            if (!present) return DocumentationGraph.empty(context.projectId());
            Node custom = Node.builder(StableId.of("custom:create-details"), NodeType.CUSTOM_HTML_SECTION,
                            "Create details")
                    .description(description)
                    .metadata(ElementMetadata.builder().evidence(List.of(Evidence.humanReviewed(
                            "mandala/custom/create/details.html", description))).build())
                    .attributes(Map.of("references", List.of("flow:create"))).build();
            return DocumentationGraph.of(context.projectId(), context.targetCommit(), context.analyzedAt(),
                    List.of(custom), List.of());
        }
    }
}
