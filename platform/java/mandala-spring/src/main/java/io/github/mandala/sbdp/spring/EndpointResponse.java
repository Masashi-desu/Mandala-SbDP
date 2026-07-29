package io.github.mandala.sbdp.spring;

import java.util.Objects;

public record EndpointResponse(String status, String type, String mediaType, String description) {
    public EndpointResponse {
        status = Objects.requireNonNullElse(status, "default");
        type = Objects.requireNonNullElse(type, "");
        mediaType = Objects.requireNonNullElse(mediaType, "");
        description = Objects.requireNonNullElse(description, "");
    }
}
