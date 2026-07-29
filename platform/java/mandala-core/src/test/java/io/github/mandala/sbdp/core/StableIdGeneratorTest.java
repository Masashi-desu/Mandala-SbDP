package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StableIdGeneratorTest {
    private final StableIdGenerator ids = new StableIdGenerator();

    @Test
    void normalizesRoutesWithoutUsingQueryOrSpringRegex() {
        assertEquals(StableId.of("endpoint:POST:/api/projects/{id}"),
                ids.endpoint("post", "https://localhost:8080//api/projects/{id:[0-9]+}/?debug=true"));
        assertEquals(StableId.of("screen:/projects/new"), ids.screen("projects/new/"));
        assertEquals(StableId.of("endpoint:GET:/x/{id}"), ids.endpoint("GET", "/x/{id:[0-9]{2}}"));
        assertEquals(StableId.of("endpoint:GET:/x/{id}"), ids.endpoint("GET", "/x/{id:[0-9]?}"));
        assertThrows(IllegalArgumentException.class, () -> ids.endpoint("GET", "/x/{id:[0-9]+"));
    }

    @Test
    void normalizesDatabaseAndResourceIdentities() {
        assertEquals("table:public.projects", ids.table("PUBLIC", "Projects").value());
        assertEquals("table:public.MixedCase", ids.table("public", "\"MixedCase\"").value());
        assertEquals("sql:META-INF/example/Dao/select.sql",
                ids.sql("./META-INF/example/Dao/select.sql").value());
        assertThrows(IllegalArgumentException.class, () -> ids.sql("../secret.sql"));
        assertThrows(IllegalArgumentException.class, () -> ids.sql("/tmp/secret.sql"));
        assertThrows(IllegalArgumentException.class, () -> ids.sql("C:/Users/secret.sql"));
    }

    @Test
    void canonicalizesJavaMemberParameterNamesAndRejectsMalformedOwners() {
        assertEquals(ids.javaSymbol("example.Service", "create(Request request)"),
                ids.javaSymbol("example.Service", "create(Request)"));
        assertEquals(ids.dao("example.Dao", "find(java.util.Map<String, Long> keys, int limit)"),
                ids.dao("example.Dao", "find(java.util.Map<String,Long>,int)"));
        assertEquals(ids.javaSymbol("example.Service", "m(java.util.List<? extends Foo> values)"),
                ids.javaSymbol("example.Service", "m(java.util.List<? extends Foo>)"));
        assertEquals(ids.javaSymbol("example.Service", "m(@Valid Request value)"),
                ids.javaSymbol("example.Service", "m(Request)"));
        assertThrows(IllegalArgumentException.class, () -> ids.javaSymbol("example..Service"));
    }

    @Test
    void edgeIdsAreStableAndDirectionSensitive() {
        StableId a = StableId.of("java:example.A");
        StableId b = StableId.of("java:example.B");
        assertEquals(ids.edge(EdgeType.CALLS, a, b), ids.edge(EdgeType.CALLS, a, b));
        assertNotEquals(ids.edge(EdgeType.CALLS, a, b), ids.edge(EdgeType.CALLS, b, a));
    }
}
