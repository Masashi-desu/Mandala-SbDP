package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceScope;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.ReviewState;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Implements the documented source hierarchy separately for technical facts and design intent. */
public final class ConfidenceEvaluator {
    public Confidence evaluate(ElementMetadata metadata) {
        if (metadata.conflicted()) return Confidence.CONFLICTED;
        if (metadata.stale().stale()) return Confidence.STALE;
        if ((metadata.reviewState() == ReviewState.HUMAN_REVIEWED || metadata.reviewState() == ReviewState.APPROVED)
                && metadata.evidence().stream().anyMatch(evidence -> evidence.type() == EvidenceType.HUMAN_INPUT)) {
            return Confidence.HUMAN_REVIEWED;
        }
        return metadata.evidence().stream().map(Evidence::confidence)
                .max(Comparator.comparingInt(this::confidenceRank)).orElse(Confidence.UNKNOWN);
    }

    public Optional<Evidence> strongest(Collection<Evidence> evidence, EvidenceScope scope) {
        return evidence.stream().filter(item -> item.scope() == scope)
                .max(Comparator.comparingInt(item -> sourceRank(item.type(), scope)));
    }

    public int sourceRank(EvidenceType type, EvidenceScope scope) {
        if (scope == EvidenceScope.DESIGN_INTENT) {
            return switch (type) {
                case HUMAN_INPUT -> 700;
                case JAVADOC -> 500;
                case SOURCE_CODE -> 400;
                case AGENT_INFERENCE -> 100;
                default -> 200;
            };
        }
        return switch (type) {
            case RUNTIME_OBSERVATION, PLAYWRIGHT_OBSERVATION -> 700;
            case SPRING_MAPPING -> 600;
            case DATABASE_INTROSPECTION -> 550;
            case OPENAPI -> 500;
            case SOURCE_CODE, JAVADOC, DOMA_MAPPING, SQL_STATIC_ANALYSIS -> 400;
            case HUMAN_INPUT -> 300;
            case AGENT_INFERENCE -> 100;
        };
    }

    public int metadataAuthority(ElementMetadata metadata, EvidenceScope scope) {
        return strongest(metadata.evidence(), scope).map(evidence -> sourceRank(evidence.type(), scope)).orElse(0);
    }

    private int confidenceRank(Confidence confidence) {
        return switch (confidence) {
            case HUMAN_REVIEWED -> 600;
            case OBSERVED -> 500;
            case DECLARED -> 400;
            case INFERRED -> 300;
            case UNKNOWN -> 0;
            case STALE -> -100;
            case CONFLICTED -> -200;
        };
    }
}
