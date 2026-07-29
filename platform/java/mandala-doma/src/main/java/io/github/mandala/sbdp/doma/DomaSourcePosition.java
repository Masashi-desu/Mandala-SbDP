package io.github.mandala.sbdp.doma;

import java.nio.file.Path;
import java.util.Objects;

public record DomaSourcePosition(Path file, int line, int column) {
    public DomaSourcePosition {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("line and column must be positive");
        }
    }
}
