package io.github.mandala.sbdp.spring;

import java.nio.file.Path;
import java.util.Objects;

public record SourcePosition(Path file, int line, int column) {
    public SourcePosition {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("line and column must be positive");
        }
    }
}
