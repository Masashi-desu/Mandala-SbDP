package io.github.mandala.sbdp.model;

import java.time.Instant;
import java.util.Objects;

public record StaleInfo(
        boolean stale,
        String reason,
        Instant detectedAt,
        String sourceFingerprint,
        String currentFingerprint,
        StaleCause cause
) {
    public StaleInfo {
        reason = Objects.requireNonNullElse(reason, "").strip();
        sourceFingerprint = Objects.requireNonNullElse(sourceFingerprint, "").strip();
        currentFingerprint = Objects.requireNonNullElse(currentFingerprint, "").strip();
        cause = cause == null ? StaleCause.UNKNOWN : cause;
        if (stale && reason.isBlank()) {
            throw new IllegalArgumentException("A stale item requires a reason");
        }
    }

    public static StaleInfo fresh() {
        return new StaleInfo(false, "", null, "", "", StaleCause.UNKNOWN);
    }

    public static StaleInfo stale(String reason, Instant detectedAt, String sourceFingerprint,
                                  String currentFingerprint) {
        return new StaleInfo(true, reason, detectedAt, sourceFingerprint, currentFingerprint, StaleCause.UNKNOWN);
    }

    public static StaleInfo stale(StaleCause cause, String reason, Instant detectedAt, String sourceFingerprint,
                                  String currentFingerprint) {
        return new StaleInfo(true, reason, detectedAt, sourceFingerprint, currentFingerprint, cause);
    }
}
