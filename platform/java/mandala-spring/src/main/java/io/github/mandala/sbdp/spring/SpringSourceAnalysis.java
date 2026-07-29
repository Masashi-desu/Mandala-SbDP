package io.github.mandala.sbdp.spring;

import java.util.List;

public record SpringSourceAnalysis(
        List<EndpointDescriptor> endpoints,
        List<JavaSymbolDescriptor> symbols,
        List<ErrorResponseDescriptor> errorResponses,
        List<String> warnings) {
    public SpringSourceAnalysis {
        endpoints = List.copyOf(endpoints == null ? List.of() : endpoints);
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
        errorResponses = List.copyOf(errorResponses == null ? List.of() : errorResponses);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
