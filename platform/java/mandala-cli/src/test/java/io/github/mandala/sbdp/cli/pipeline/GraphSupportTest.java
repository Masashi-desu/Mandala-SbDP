package io.github.mandala.sbdp.cli.pipeline;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphSupportTest {
    @Test
    void canonicalizesUnorderedCollectionsForAttributesAndFingerprints() {
        Set<String> forward = new LinkedHashSet<>(List.of("alpha", "beta", "gamma"));
        Set<String> reverse = new LinkedHashSet<>(List.of("gamma", "beta", "alpha"));

        assertEquals(List.of("alpha", "beta", "gamma"), GraphSupport.serializable(reverse));
        assertEquals(GraphSupport.fingerprint(Map.of("values", forward)),
                GraphSupport.fingerprint(Map.of("values", reverse)));
    }
}
