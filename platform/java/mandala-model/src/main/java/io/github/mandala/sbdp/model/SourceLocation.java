package io.github.mandala.sbdp.model;

import java.util.Objects;

/** Source locations are supporting metadata and are deliberately excluded from stable ids. */
public record SourceLocation(
        String path,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        String symbol
) implements Comparable<SourceLocation> {
    public SourceLocation {
        path = normalizePath(Objects.requireNonNull(path, "path"));
        symbol = symbol == null ? "" : symbol.strip();
        if (path.isBlank()) {
            throw new IllegalArgumentException("Source path must not be blank");
        }
        if (startLine < 0 || startColumn < 0 || endLine < 0 || endColumn < 0) {
            throw new IllegalArgumentException("Source coordinates must not be negative");
        }
        if (startLine > 0 && endLine > 0 && endLine < startLine) {
            throw new IllegalArgumentException("End line must not precede start line");
        }
    }

    public static SourceLocation of(String path) {
        return new SourceLocation(path, 0, 0, 0, 0, "");
    }

    public static SourceLocation line(String path, int line) {
        return new SourceLocation(path, line, 0, line, 0, "");
    }

    private static String normalizePath(String path) {
        String normalized = path.strip().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    @Override
    public int compareTo(SourceLocation other) {
        int byPath = path.compareTo(other.path);
        if (byPath != 0) return byPath;
        int byLine = Integer.compare(startLine, other.startLine);
        if (byLine != 0) return byLine;
        int byColumn = Integer.compare(startColumn, other.startColumn);
        if (byColumn != 0) return byColumn;
        int byEndLine = Integer.compare(endLine, other.endLine);
        if (byEndLine != 0) return byEndLine;
        int byEndColumn = Integer.compare(endColumn, other.endColumn);
        if (byEndColumn != 0) return byEndColumn;
        return symbol.compareTo(other.symbol);
    }
}
