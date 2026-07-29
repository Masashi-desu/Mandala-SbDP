package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.StableId;

public record ValidationIssue(ValidationSeverity severity, String code, StableId subjectId, String message) {
}
