package io.github.mandala.sbdp.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StableIdTest {
    @Test
    void exposesNamespaceAndLocalPart() {
        StableId id = StableId.of("endpoint:POST:/api/projects");

        assertEquals("endpoint", id.namespace());
        assertEquals("POST:/api/projects", id.localPart());
        assertEquals("endpoint:POST:/api/projects", id.toString());
    }

    @Test
    void rejectsIdsWithoutNamespaceOrWithTransientWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> StableId.of("projects"));
        assertThrows(IllegalArgumentException.class, () -> StableId.of("sql:"));
        assertThrows(IllegalArgumentException.class, () -> StableId.of("screen:/project list"));
        assertThrows(IllegalArgumentException.class, () -> StableId.of(" "));
    }
}
