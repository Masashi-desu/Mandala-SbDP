package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Evidence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvidenceMerger {
    public List<Evidence> merge(Collection<? extends Collection<Evidence>> sources) {
        Map<String, Evidence> merged = new LinkedHashMap<>();
        sources.stream().flatMap(Collection::stream).sorted().forEach(evidence ->
                merged.merge(evidence.id().value(), evidence, EvidenceMerger::richer));
        return merged.values().stream().sorted().toList();
    }

    @SafeVarargs
    public final List<Evidence> merge(Collection<Evidence>... sources) {
        return merge(List.of(sources));
    }

    private static Evidence richer(Evidence left, Evidence right) {
        int leftScore = richness(left);
        int rightScore = richness(right);
        if (rightScore != leftScore) return rightScore > leftScore ? right : left;
        return canonicalKey(right).compareTo(canonicalKey(left)) > 0 ? right : left;
    }

    private static int richness(Evidence evidence) {
        int score = evidence.description().length() + evidence.details().size() * 10;
        if (evidence.sourceLocation() != null) score += 20;
        if (!evidence.targetCommit().isBlank()) score += 10;
        if (evidence.observedAt() != null) score += 5;
        if (!evidence.adapter().isBlank()) score += 5;
        return score;
    }

    private static String canonicalKey(Evidence evidence) {
        return evidence.type() + "\u0000" + evidence.scope() + "\u0000" + evidence.source() + "\u0000"
                + evidence.description() + "\u0000" + String.valueOf(evidence.sourceLocation()) + "\u0000"
                + evidence.targetCommit() + "\u0000" + String.valueOf(evidence.observedAt()) + "\u0000"
                + evidence.adapter() + "\u0000" + evidence.confidence() + "\u0000" + evidence.details();
    }
}
