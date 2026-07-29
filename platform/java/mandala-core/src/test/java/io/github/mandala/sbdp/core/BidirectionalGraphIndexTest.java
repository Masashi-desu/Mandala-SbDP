package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidirectionalGraphIndexTest {
    @Test
    void supportsReverseLookupAndPathsFromOnlyForwardEdges() {
        Node flow = Node.of("flow:create", NodeType.E2E_FLOW, "Create");
        Node endpoint = Node.of("endpoint:POST:/projects", NodeType.HTTP_ENDPOINT, "POST projects");
        Node table = Node.of("table:public.projects", NodeType.DB_TABLE, "projects");
        Edge calls = Edge.of("edge:calls:1", EdgeType.CALLS_HTTP, flow.id(), endpoint.id());
        Edge creates = Edge.of("edge:creates:1", EdgeType.CREATES, endpoint.id(), table.id());
        DocumentationGraph graph = DocumentationGraph.of("p", "", null,
                List.of(flow, endpoint, table), List.of(calls, creates));
        BidirectionalGraphIndex index = new BidirectionalGraphIndex(graph);

        assertEquals(flow.id(), index.predecessors(endpoint.id()).getFirst().id());
        assertEquals(endpoint.id(), index.predecessors(table.id()).getFirst().id());
        assertTrue(index.traverse(Set.of(table.id()), TraversalDirection.INCOMING, 2, Set.of())
                .contains(flow.id()));
        assertEquals(List.of(table.id(), endpoint.id(), flow.id()),
                index.shortestPath(table.id(), flow.id(), TraversalDirection.BOTH, 4));
    }
}
