package io.github.mandala.sbdp.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OtlpJsonTraceImporterTest {
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void importsTraceHierarchyClassifiesBoundariesAndMasksSensitiveValues() throws Exception {
        String json = payload();
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T01:02:03Z"), ZoneOffset.UTC);
        OtlpJsonTraceImporter importer = new OtlpJsonTraceImporter(new SensitiveDataMasker(), clock);

        OtlpTraceBatch batch = importer.importJson(new ObjectMapper().readTree(json));

        assertEquals(Instant.parse("2026-07-22T01:02:03Z"), batch.importedAt());
        assertEquals(1, batch.traces().size());
        RuntimeTrace trace = batch.traces().getFirst();
        assertEquals(2, trace.spans().size());
        assertEquals(1, trace.rootSpans().size());
        RuntimeSpan server = trace.spans().stream().filter(span -> span.spanId().equals("1111111111111111"))
                .findFirst().orElseThrow();
        RuntimeSpan database = trace.spans().stream().filter(span -> span.spanId().equals("2222222222222222"))
                .findFirst().orElseThrow();
        assertEquals(SpanBoundary.HTTP_SERVER, server.boundary());
        assertEquals("[REDACTED]", server.attributes().get("http.request.header.authorization"));
        assertEquals("[REDACTED]", server.resourceAttributes().get("session.id"));
        assertEquals("[REDACTED]", server.events().getFirst().attributes().get("password"));
        assertEquals(SpanBoundary.DOMA_DAO, database.boundary());
        String sanitizedSql = String.valueOf(database.attributes().get("db.statement"));
        assertFalse(sanitizedSql.contains("private-project"));
        assertFalse(sanitizedSql.contains("42"));
        assertTrue(sanitizedSql.contains("?"));
        assertEquals(RuntimeStatus.Code.ERROR, database.status().code());
        assertEquals(1000, database.duration().toNanos());
        assertTrue(batch.warnings().isEmpty(), () -> String.join("\n", batch.warnings()));
    }

    @Test
    void transparentlyReadsGzipOtlpJson() throws Exception {
        Path gzip = temporaryDirectory.resolve("trace.json.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        OtlpTraceBatch batch = new OtlpJsonTraceImporter().importFile(gzip);

        assertEquals(1, batch.traces().size());
        assertEquals(TRACE_ID, batch.traces().getFirst().traceId());
    }

    @Test
    void acceptsAdditionalApplicationSpecificMaskKeys() {
        MaskingConfiguration configuration = new MaskingConfiguration(
                Set.of("tenant.customer-number"), List.of(), "***", true);
        SensitiveDataMasker masker = new SensitiveDataMasker(configuration);

        assertEquals("***", masker.maskValue("tenant.customer-number", "C-123"));
        assertEquals("visible", masker.maskValue("tenant.name", "visible"));
    }

    @Test
    void acceptsSnakeCaseAndAnArrayOfRawResourceSpanObjects() throws Exception {
        String json = """
                [{
                  "resource": {"attributes": []},
                  "scope_spans": [{
                    "scope": {"name": "manual"},
                    "spans": [{
                      "trace_id": "0123456789abcdef0123456789abcdef",
                      "span_id": "3333333333333333",
                      "name": "consumer",
                      "kind": 5,
                      "start_time_unix_nano": "1",
                      "end_time_unix_nano": "2",
                      "attributes": []
                    }]
                  }]
                }]
                """;

        OtlpTraceBatch batch = new OtlpJsonTraceImporter().importJson(new ObjectMapper().readTree(json));

        assertEquals(RuntimeSpanKind.CONSUMER, batch.traces().getFirst().spans().getFirst().kind());
        assertEquals(SpanBoundary.ASYNC, batch.traces().getFirst().spans().getFirst().boundary());
    }

    private String payload() {
        return """
                {
                  "resourceSpans": [{
                    "resource": {
                      "attributes": [
                        {"key": "service.name", "value": {"stringValue": "sample-backend"}},
                        {"key": "session.id", "value": {"stringValue": "session-secret"}}
                      ]
                    },
                    "scopeSpans": [{
                      "scope": {"name": "io.opentelemetry.spring", "version": "1.51.0"},
                      "spans": [
                        {
                          "traceId": "%s",
                          "spanId": "1111111111111111",
                          "name": "GET /api/projects/{id}",
                          "kind": "SPAN_KIND_SERVER",
                          "startTimeUnixNano": "1753146000000000000",
                          "endTimeUnixNano": "1753146000000010000",
                          "attributes": [
                            {"key": "http.route", "value": {"stringValue": "/api/projects/{id}"}},
                            {"key": "http.request.method", "value": {"stringValue": "GET"}},
                            {"key": "http.request.header.authorization", "value": {"stringValue": "Bearer secret"}}
                          ],
                          "events": [{
                            "name": "exception",
                            "timeUnixNano": "1753146000000005000",
                            "attributes": [{"key": "password", "value": {"stringValue": "never-save"}}]
                          }],
                          "status": {"code": "STATUS_CODE_OK"}
                        },
                        {
                          "traceId": "%s",
                          "spanId": "2222222222222222",
                          "parentSpanId": "1111111111111111",
                          "name": "ProjectDao.findById",
                          "kind": "SPAN_KIND_INTERNAL",
                          "startTimeUnixNano": "1753146000000006000",
                          "endTimeUnixNano": "1753146000000007000",
                          "attributes": [
                            {"key": "mandala.layer", "value": {"stringValue": "dao"}},
                            {"key": "db.system", "value": {"stringValue": "postgresql"}},
                            {"key": "db.statement", "value": {"stringValue": "select * from projects where id = 42 and name = 'private-project'"}}
                          ],
                          "status": {"code": "STATUS_CODE_ERROR", "message": "not found"}
                        }
                      ]
                    }]
                  }]
                }
                """.formatted(TRACE_ID, TRACE_ID);
    }
}
