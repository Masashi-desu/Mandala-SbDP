package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.ConflictStatus;
import io.github.mandala.sbdp.model.ConflictType;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.model.ReviewState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class ConflictDetector {
    private final EvidenceMerger evidenceMerger = new EvidenceMerger();

    public List<Conflict> detect(Node left, Node right, Instant detectedAt) {
        requireSameId(left.id(), right.id());
        List<Conflict> conflicts = new ArrayList<>();
        if (left.type() != right.type()) {
            conflicts.add(conflict(left.id(), ConflictType.TYPE_MISMATCH, "type",
                    pair(left.type(), right.type()), evidence(left, right), detectedAt));
        }
        if (incompatible(left.metadata().reviewState(), right.metadata().reviewState())) {
            conflicts.add(conflict(left.id(), ConflictType.SOURCE_DISAGREEMENT, "reviewState",
                    pair(left.metadata().reviewState(), right.metadata().reviewState()), evidence(left, right), detectedAt));
        }
        // Display names and prose from complementary sources are alternatives, not logical contradictions.
        // Structured claims belong in attributes (or Custom HTML assertions), which are compared below.
        boolean runtime = runtimeNode(left) || runtimeNode(right);
        Map<String, Object> leftAttributes = SemanticAttributes.normalize(left.attributes(), runtime);
        Map<String, Object> rightAttributes = SemanticAttributes.normalize(right.attributes(), runtime);
        commonKeys(leftAttributes, rightAttributes).stream()
                .filter(key -> !Objects.deepEquals(leftAttributes.get(key), rightAttributes.get(key)))
                .forEach(key -> conflicts.add(conflict(left.id(), ConflictType.ATTRIBUTE_MISMATCH,
                        "attributes." + key, "Attribute " + quoted(key) + " has conflicting values",
                        evidence(left, right), detectedAt)));
        return List.copyOf(conflicts);
    }

    public List<Conflict> detect(Edge left, Edge right, Instant detectedAt) {
        requireSameId(left.id(), right.id());
        List<Conflict> conflicts = new ArrayList<>();
        if (left.type() != right.type()) {
            conflicts.add(conflict(left.id(), ConflictType.TYPE_MISMATCH, "type",
                    pair(left.type(), right.type()), evidence(left, right), detectedAt));
        }
        if (incompatible(left.metadata().reviewState(), right.metadata().reviewState())) {
            conflicts.add(conflict(left.id(), ConflictType.SOURCE_DISAGREEMENT, "reviewState",
                    pair(left.metadata().reviewState(), right.metadata().reviewState()), evidence(left, right), detectedAt));
        }
        if (!left.from().equals(right.from())) {
            conflicts.add(conflict(left.id(), ConflictType.TYPE_MISMATCH, "from",
                    pair(left.from(), right.from()), evidence(left, right), detectedAt));
        }
        if (!left.to().equals(right.to())) {
            conflicts.add(conflict(left.id(), ConflictType.TYPE_MISMATCH, "to",
                    pair(left.to(), right.to()), evidence(left, right), detectedAt));
        }
        boolean runtime = runtimeMetadata(left.metadata()) || runtimeMetadata(right.metadata());
        Map<String, Object> leftAttributes = SemanticAttributes.normalize(left.attributes(), runtime);
        Map<String, Object> rightAttributes = SemanticAttributes.normalize(right.attributes(), runtime);
        commonKeys(leftAttributes, rightAttributes).stream()
                .filter(key -> !Objects.deepEquals(leftAttributes.get(key), rightAttributes.get(key)))
                .forEach(key -> conflicts.add(conflict(left.id(), ConflictType.ATTRIBUTE_MISMATCH,
                        "attributes." + key, "Attribute " + quoted(key) + " has conflicting values",
                        evidence(left, right), detectedAt)));
        return List.copyOf(conflicts);
    }

    public Conflict dangling(Edge edge, StableId missingNode, Instant detectedAt) {
        return conflict(edge.id(), ConflictType.DANGLING_EDGE, "endpoint",
                "Edge references missing node " + missingNode, edge.metadata().evidence(), detectedAt);
    }

    private List<Evidence> evidence(Node left, Node right) {
        return evidenceMerger.merge(left.metadata().evidence(), right.metadata().evidence());
    }

    private List<Evidence> evidence(Edge left, Edge right) {
        return evidenceMerger.merge(left.metadata().evidence(), right.metadata().evidence());
    }

    private Conflict conflict(StableId subject, ConflictType type, String field, String description,
                              Collection<Evidence> evidence, Instant detectedAt) {
        String material = subject + "\u0000" + type + "\u0000" + field + "\u0000" + description;
        return new Conflict(StableId.of("conflict:" + StableIdGenerator.digest(material)), type, subject, field,
                description, List.copyOf(evidence), detectedAt, ConflictStatus.OPEN, "");
    }

    private static TreeSet<String> commonKeys(Map<String, Object> left, Map<String, Object> right) {
        TreeSet<String> keys = new TreeSet<>(left.keySet());
        keys.retainAll(right.keySet());
        return keys;
    }

    private static void requireSameId(StableId left, StableId right) {
        if (!left.equals(right)) throw new IllegalArgumentException("Conflict comparison requires equal ids");
    }

    private static String pair(Object left, Object right) {
        String first = String.valueOf(left);
        String second = String.valueOf(right);
        return first.compareTo(second) <= 0 ? first + " conflicts with " + second
                : second + " conflicts with " + first;
    }

    private static String quoted(String value) {
        return "'" + value + "'";
    }

    private static boolean incompatible(ReviewState left, ReviewState right) {
        return (left == ReviewState.REJECTED && (right == ReviewState.APPROVED || right == ReviewState.HUMAN_REVIEWED))
                || (right == ReviewState.REJECTED
                && (left == ReviewState.APPROVED || left == ReviewState.HUMAN_REVIEWED));
    }

    private static boolean runtimeNode(Node node) {
        return node.type() == io.github.mandala.sbdp.model.NodeType.TRACE
                || node.type() == io.github.mandala.sbdp.model.NodeType.SPAN
                || runtimeMetadata(node.metadata());
    }

    private static boolean runtimeMetadata(io.github.mandala.sbdp.model.ElementMetadata metadata) {
        return metadata.evidence().stream().anyMatch(evidence ->
                evidence.type() == io.github.mandala.sbdp.model.EvidenceType.RUNTIME_OBSERVATION
                        || evidence.type() == io.github.mandala.sbdp.model.EvidenceType.PLAYWRIGHT_OBSERVATION);
    }
}
