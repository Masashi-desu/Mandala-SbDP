package io.github.mandala.sbdp.doma;

import java.util.Objects;

public record TemplateDirective(Type type, String expression, int offset) {
    public TemplateDirective {
        type = Objects.requireNonNull(type, "type");
        expression = Objects.requireNonNullElse(expression, "");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }

    public enum Type {
        IF,
        ELSEIF,
        ELSE,
        END,
        FOR,
        EXPAND,
        POPULATE,
        BIND_VARIABLE,
        LITERAL_VARIABLE,
        EMBEDDED_VARIABLE,
        OTHER
    }
}
