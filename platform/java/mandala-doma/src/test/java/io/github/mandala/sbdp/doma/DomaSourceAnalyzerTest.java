package io.github.mandala.sbdp.doma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mandala.sbdp.doma.sql.CrudOperation;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DomaSourceAnalyzerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void linksDaoMethodToConventionalExternalSqlAndParsesIt() throws Exception {
        Path javaRoot = temporaryDirectory.resolve("src/main/java");
        Path resourcesRoot = temporaryDirectory.resolve("src/main/resources");
        Path dao = javaRoot.resolve("com/example/ProjectDao.java");
        Path sql = resourcesRoot.resolve("META-INF/com/example/ProjectDao/findActive.sql");
        Files.createDirectories(dao.getParent());
        Files.createDirectories(sql.getParent());
        Files.writeString(dao, """
                package com.example;
                @Dao
                interface ProjectDao {
                    /** Finds active projects. */
                    @Select
                    java.util.List<Project> findActive(boolean admin, @Bind("ownerId") long ownerId);
                }
                """);
        Files.writeString(sql, """
                select p.id, p.name
                  from public.projects p
                 where
                /*%if admin */
                   true
                /*%else*/
                   p.owner_id = /* ownerId */0
                /*%end*/
                   and p.deleted_at is null
                """);

        DomaAnalysis analysis = new DomaSourceAnalyzer().analyze(javaRoot, resourcesRoot);

        assertEquals(1, analysis.daos().size());
        DomaMethodDescriptor method = analysis.daos().getFirst().methods().getFirst();
        assertEquals("dao:com.example.ProjectDao#findActive(boolean,long)", method.stableId());
        assertTrue(method.sqlFileDeclared());
        assertEquals(sql.toAbsolutePath(), method.externalSqlFile());
        assertEquals(1, analysis.sqlMappings().size());
        ExternalSqlMapping mapping = analysis.sqlMappings().getFirst();
        assertEquals(method.stableId(), mapping.daoMethodId());
        assertTrue(mapping.statements().getFirst().tables().getFirst().operations().contains(CrudOperation.READ));
        assertTrue(mapping.statements().getFirst().columns().stream()
                .anyMatch(column -> column.column().equals("owner_id")));
        assertTrue(mapping.warnings().isEmpty(), () -> String.join("\n", mapping.warnings()));
    }

    @Test
    void givesOverloadedDaoMethodsDistinctCanonicalIds() throws Exception {
        Path javaRoot = temporaryDirectory.resolve("overload/main/java");
        Path resourcesRoot = temporaryDirectory.resolve("overload/main/resources");
        Path dao = javaRoot.resolve("com/example/AuditDao.java");
        Files.createDirectories(dao.getParent());
        Files.createDirectories(resourcesRoot);
        Files.writeString(dao, """
                package com.example;
                @Dao
                interface AuditDao {
                    @Procedure void record(long id);
                    @Procedure void record(String id);
                }
                """);

        DomaAnalysis analysis = new DomaSourceAnalyzer().analyze(javaRoot, resourcesRoot);

        assertEquals(java.util.Set.of(
                        "dao:com.example.AuditDao#record(long)",
                        "dao:com.example.AuditDao#record(String)"),
                analysis.daos().getFirst().methods().stream()
                        .map(DomaMethodDescriptor::stableId).collect(java.util.stream.Collectors.toSet()));
    }
}
