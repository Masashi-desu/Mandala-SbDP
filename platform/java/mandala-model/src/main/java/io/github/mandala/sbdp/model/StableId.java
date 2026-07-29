package io.github.mandala.sbdp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/** A durable identifier whose value must not depend on source line numbers or runtime trace ids. */
public record StableId(@JsonValue String value) implements Comparable<StableId> {
    private static final int MAX_LENGTH = 1_024;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public StableId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Stable id must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Stable id must not exceed " + MAX_LENGTH + " characters");
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Stable id must have a namespace and a non-empty local part");
        }
        if (value.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException("Stable id must not contain whitespace or control characters");
        }
    }

    public static StableId of(String value) {
        return new StableId(value);
    }

    public String namespace() {
        return value.substring(0, value.indexOf(':'));
    }

    public String localPart() {
        return value.substring(value.indexOf(':') + 1);
    }

    @Override
    public int compareTo(StableId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
