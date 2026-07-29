package io.github.mandala.sbdp.spring;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record ReconciledEndpoint(
        String stableId,
        EndpointDescriptor canonical,
        Set<EndpointSource> declarationSources,
        List<EndpointDescriptor> declarations,
        List<String> conflicts) {
    public ReconciledEndpoint {
        declarationSources = declarationSources.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(declarationSources));
        declarations = List.copyOf(declarations);
        conflicts = List.copyOf(conflicts);
    }
}
