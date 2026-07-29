package io.github.mandala.sbdp.core;

import java.util.List;

public record ValidationReport(List<ValidationIssue> issues) {
    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    public List<ValidationIssue> errors() {
        return issues.stream().filter(issue -> issue.severity() == ValidationSeverity.ERROR).toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream().filter(issue -> issue.severity() == ValidationSeverity.WARNING).toList();
    }
}
