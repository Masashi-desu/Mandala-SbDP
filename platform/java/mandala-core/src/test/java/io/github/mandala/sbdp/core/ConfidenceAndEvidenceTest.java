package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.ConflictStatus;
import io.github.mandala.sbdp.model.ConflictType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceScope;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.ReviewState;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.model.StaleInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfidenceAndEvidenceTest {
    @Test
    void mergesDuplicateEvidenceAndKeepsRicherRepresentation() {
        StableId id = StableId.of("evidence:one");
        Evidence sparse = new Evidence(id, EvidenceType.SOURCE_CODE, EvidenceScope.TECHNICAL_FACT,
                "source", "same", null, "", null, "", Confidence.INFERRED, Map.of());
        Evidence rich = new Evidence(id, EvidenceType.SOURCE_CODE, EvidenceScope.TECHNICAL_FACT,
                "source", "same", null, "commit", Instant.EPOCH, "java", Confidence.INFERRED,
                Map.of("symbol", "A#m"));

        List<Evidence> merged = new EvidenceMerger().merge(List.of(sparse), List.of(rich));

        assertEquals(List.of(rich), merged);
    }

    @Test
    void confidenceUsesObservedButConflictAndStaleTakePrecedence() {
        ConfidenceEvaluator evaluator = new ConfidenceEvaluator();
        Evidence inferred = Evidence.of(EvidenceType.AGENT_INFERENCE, "agent", "guess");
        Evidence observed = Evidence.of(EvidenceType.RUNTIME_OBSERVATION, "trace", "seen");
        ElementMetadata base = ElementMetadata.builder().evidence(List.of(inferred, observed)).build();
        assertEquals(Confidence.OBSERVED, evaluator.evaluate(base));

        ElementMetadata stale = new ElementMetadata(base.evidence(), base.sourceLocations(), "", null, "",
                Confidence.UNKNOWN, ReviewState.UNREVIEWED,
                StaleInfo.stale("changed", Instant.EPOCH, "a", "b"), List.of(), List.of(), null, null);
        assertEquals(Confidence.STALE, evaluator.evaluate(stale));

        Conflict conflict = new Conflict(StableId.of("conflict:x"), ConflictType.SOURCE_DISAGREEMENT,
                StableId.of("java:A"), "description", "Sources disagree", List.of(), Instant.EPOCH,
                ConflictStatus.OPEN, "");
        ElementMetadata conflicted = new ElementMetadata(base.evidence(), base.sourceLocations(), "", null, "",
                Confidence.UNKNOWN, ReviewState.UNREVIEWED, stale.stale(), List.of(conflict), List.of(), null, null);
        assertEquals(Confidence.CONFLICTED, evaluator.evaluate(conflicted));
    }

    @Test
    void appliesDifferentAuthorityOrderingToFactsAndIntent() {
        ConfidenceEvaluator evaluator = new ConfidenceEvaluator();
        Evidence human = new Evidence(null, EvidenceType.HUMAN_INPUT, EvidenceScope.DESIGN_INTENT,
                "review", "intent", null, "", null, "", null, Map.of());
        Evidence runtime = new Evidence(null, EvidenceType.RUNTIME_OBSERVATION, EvidenceScope.TECHNICAL_FACT,
                "trace", "fact", null, "", null, "", null, Map.of());

        assertEquals(human, evaluator.strongest(List.of(runtime, human), EvidenceScope.DESIGN_INTENT).orElseThrow());
        assertEquals(runtime, evaluator.strongest(List.of(runtime, human), EvidenceScope.TECHNICAL_FACT).orElseThrow());
    }
}
