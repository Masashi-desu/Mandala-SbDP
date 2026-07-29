package io.github.mandala.sbdp.spring;

import java.util.List;

public record EndpointDiscovery(List<EndpointDescriptor> endpoints, List<String> warnings) {
    public EndpointDiscovery {
        endpoints = List.copyOf(endpoints == null ? List.of() : endpoints);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
