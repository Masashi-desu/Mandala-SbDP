package io.github.mandala.sbdp.opentelemetry;

import java.util.Objects;

public record RuntimeStatus(Code code, String message) {
    public RuntimeStatus {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNullElse(message, "");
    }

    public enum Code {
        UNSET,
        OK,
        ERROR
    }
}
