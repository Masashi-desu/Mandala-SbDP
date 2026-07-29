package io.github.mandala.sbdp.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphProjectionTest {
    @Test
    void createsSchemaRuntimeAndUiViewsWithoutMutatingSourceGraph() {
        DocumentationGraph graph = DocumentationGraph.of("p", "", null, List.of(
                Node.of("table:public.projects", NodeType.DB_TABLE, "projects"),
                Node.of("trace:t", NodeType.TRACE, "trace"),
                Node.of("screen:/projects", NodeType.SCREEN, "Projects"),
                Node.of("java:example.Service", NodeType.JAVA_CLASS, "Service")
        ), List.of());

        assertEquals(1, SchemaGraph.from(graph).nodes().size());
        assertEquals(1, RuntimeGraph.from(graph).nodes().size());
        assertEquals(1, UiGraph.from(graph).nodes().size());
        assertEquals(4, graph.nodes().size());
    }
}
