package io.github.mandala.sbdp.doma;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SampleDomaCompatibilityTest {
    @Test
    void parsesEveryDaoSqlInConfiguredSampleRepository() throws Exception {
        String repository = System.getenv("MANDALA_REPOSITORY_ROOT");
        Assumptions.assumeTrue(repository != null && !repository.isBlank(), "MANDALA_REPOSITORY_ROOT is not configured");
        Path backend = Path.of(repository).resolve("sample-app/backend");

        DomaAnalysis analysis = new DomaSourceAnalyzer().analyze(
                backend.resolve("src/main/java"), backend.resolve("src/main/resources"));

        assertTrue(analysis.daos().size() >= 4, () -> "Expected sample DAOs, got " + analysis.daos().size());
        assertTrue(analysis.sqlMappings().size() >= 16, () -> "Expected sample SQL files, got " + analysis.sqlMappings().size());
        assertTrue(analysis.sqlMappings().stream().allMatch(mapping -> !mapping.statements().isEmpty()),
                () -> analysis.sqlMappings().stream()
                        .filter(mapping -> mapping.statements().isEmpty())
                        .map(mapping -> mapping.sqlFile() + ": " + mapping.warnings())
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse(""));
        assertTrue(analysis.sqlMappings().stream()
                .filter(mapping -> mapping.sqlFile().getFileName().toString().equals("selectAccessible.sql"))
                .flatMap(mapping -> mapping.statements().stream())
                .flatMap(statement -> statement.columns().stream())
                .anyMatch(column -> column.column().equals("owner_id")));
    }
}
