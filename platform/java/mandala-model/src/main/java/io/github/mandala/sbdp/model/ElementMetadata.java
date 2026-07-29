package io.github.mandala.sbdp.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record ElementMetadata(
        List<Evidence> evidence,
        List<SourceLocation> sourceLocations,
        String targetCommit,
        Instant analyzedAt,
        String adapter,
        Confidence confidence,
        ReviewState reviewState,
        StaleInfo stale,
        List<Conflict> conflicts,
        List<String> warnings,
        Set<StableId> relatedTraces,
        Set<String> relatedScenarios
) {
    public ElementMetadata {
        evidence = normalizeEvidence(evidence);
        sourceLocations = sourceLocations == null ? List.of()
                : sourceLocations.stream().distinct().sorted().toList();
        targetCommit = Objects.requireNonNullElse(targetCommit, "").strip();
        adapter = Objects.requireNonNullElse(adapter, "").strip();
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
        reviewState = reviewState == null ? ReviewState.UNREVIEWED : reviewState;
        stale = stale == null ? StaleInfo.fresh() : stale;
        conflicts = normalizeConflicts(conflicts);
        warnings = warnings == null ? List.of() : warnings.stream()
                .filter(Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty()).distinct().sorted().toList();
        relatedTraces = immutableSortedSet(relatedTraces);
        relatedScenarios = immutableSortedSet(relatedScenarios);
    }

    public static ElementMetadata empty() {
        return new ElementMetadata(List.of(), List.of(), "", null, "", Confidence.UNKNOWN,
                ReviewState.UNREVIEWED, StaleInfo.fresh(), List.of(), List.of(), Set.of(), Set.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean conflicted() {
        return conflicts.stream().anyMatch(Conflict::isOpen);
    }

    private static <T extends Comparable<? super T>> Set<T> immutableSortedSet(Collection<T> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
    }

    static List<Evidence> normalizeEvidence(Collection<Evidence> values) {
        if (values == null || values.isEmpty()) return List.of();
        TreeMap<StableId, Evidence> result = new TreeMap<>();
        values.forEach(value -> result.merge(value.id(), value, ElementMetadata::richerEvidence));
        return List.copyOf(result.values());
    }

    private static Evidence richerEvidence(Evidence left, Evidence right) {
        int leftRichness = evidenceRichness(left);
        int rightRichness = evidenceRichness(right);
        if (rightRichness != leftRichness) return rightRichness > leftRichness ? right : left;
        if (right.observedAt() != null && (left.observedAt() == null || right.observedAt().isAfter(left.observedAt()))) {
            return right;
        }
        if (left.observedAt() != null && right.observedAt() == null) return left;
        return evidenceKey(right).compareTo(evidenceKey(left)) > 0 ? right : left;
    }

    private static int evidenceRichness(Evidence evidence) {
        int score = evidence.description().length() + evidence.details().size() * 10;
        if (evidence.sourceLocation() != null) score += 20;
        if (!evidence.targetCommit().isBlank()) score += 10;
        if (!evidence.adapter().isBlank()) score += 5;
        return score;
    }

    private static String evidenceKey(Evidence evidence) {
        return evidence.type() + "\u0000" + evidence.scope() + "\u0000" + evidence.source() + "\u0000"
                + evidence.description() + "\u0000" + String.valueOf(evidence.sourceLocation()) + "\u0000"
                + evidence.targetCommit() + "\u0000" + String.valueOf(evidence.observedAt()) + "\u0000"
                + evidence.adapter() + "\u0000" + evidence.confidence() + "\u0000" + evidence.details();
    }

    private static List<Conflict> normalizeConflicts(Collection<Conflict> values) {
        if (values == null || values.isEmpty()) return List.of();
        TreeMap<StableId, Conflict> result = new TreeMap<>();
        values.forEach(value -> result.merge(value.id(), value, ElementMetadata::mergeConflict));
        return List.copyOf(result.values());
    }

    private static Conflict mergeConflict(Conflict left, Conflict right) {
        if (left.type() != right.type() || !left.subjectId().equals(right.subjectId())
                || !left.field().equals(right.field())) {
            throw new IllegalArgumentException("Conflict stable id collision: " + left.id());
        }
        List<Evidence> evidence = normalizeEvidence(java.util.stream.Stream
                .concat(left.evidence().stream(), right.evidence().stream()).toList());
        ConflictStatus status = left.isOpen() || right.isOpen() ? ConflictStatus.OPEN
                : left.status() == ConflictStatus.RESOLVED || right.status() == ConflictStatus.RESOLVED
                ? ConflictStatus.RESOLVED : ConflictStatus.IGNORED;
        String resolution = java.util.stream.Stream.of(left.resolution(), right.resolution())
                .filter(value -> !value.isBlank()).max(String::compareTo).orElse("");
        if (status == ConflictStatus.RESOLVED && resolution.isBlank()) status = ConflictStatus.IGNORED;
        Instant detectedAt = left.detectedAt() == null ? right.detectedAt()
                : right.detectedAt() == null || left.detectedAt().isAfter(right.detectedAt())
                ? left.detectedAt() : right.detectedAt();
        String description = java.util.stream.Stream.of(left.description(), right.description())
                .max(java.util.Comparator.comparingInt(String::length).thenComparing(String::compareTo)).orElse("");
        return new Conflict(left.id(), left.type(), left.subjectId(), left.field(), description, evidence,
                detectedAt, status, resolution);
    }

    public static final class Builder {
        private List<Evidence> evidence = List.of();
        private List<SourceLocation> sourceLocations = List.of();
        private String targetCommit = "";
        private Instant analyzedAt;
        private String adapter = "";
        private Confidence confidence = Confidence.UNKNOWN;
        private ReviewState reviewState = ReviewState.UNREVIEWED;
        private StaleInfo stale = StaleInfo.fresh();
        private List<Conflict> conflicts = List.of();
        private List<String> warnings = List.of();
        private Set<StableId> relatedTraces = Set.of();
        private Set<String> relatedScenarios = Set.of();

        public Builder evidence(Collection<Evidence> value) { evidence = List.copyOf(value); return this; }
        public Builder sourceLocations(Collection<SourceLocation> value) { sourceLocations = List.copyOf(value); return this; }
        public Builder targetCommit(String value) { targetCommit = value; return this; }
        public Builder analyzedAt(Instant value) { analyzedAt = value; return this; }
        public Builder adapter(String value) { adapter = value; return this; }
        public Builder confidence(Confidence value) { confidence = value; return this; }
        public Builder reviewState(ReviewState value) { reviewState = value; return this; }
        public Builder stale(StaleInfo value) { stale = value; return this; }
        public Builder conflicts(Collection<Conflict> value) { conflicts = List.copyOf(value); return this; }
        public Builder warnings(Collection<String> value) { warnings = List.copyOf(value); return this; }
        public Builder relatedTraces(Collection<StableId> value) { relatedTraces = Set.copyOf(value); return this; }
        public Builder relatedScenarios(Collection<String> value) { relatedScenarios = Set.copyOf(value); return this; }

        public ElementMetadata build() {
            return new ElementMetadata(evidence, sourceLocations, targetCommit, analyzedAt, adapter, confidence,
                    reviewState, stale, conflicts, warnings, relatedTraces, relatedScenarios);
        }
    }
}
