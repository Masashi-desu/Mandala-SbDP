package io.github.mandala.sbdp.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SampleSpringCompatibilityTest {
    @Test
    void discoversControllersServicesAndCrudEndpointsInConfiguredSampleRepository() throws Exception {
        String repository = System.getenv("MANDALA_REPOSITORY_ROOT");
        Assumptions.assumeTrue(repository != null && !repository.isBlank(), "MANDALA_REPOSITORY_ROOT is not configured");
        Path javaRoot = Path.of(repository).resolve("sample-app/backend/src/main/java");

        SpringSourceAnalysis analysis = new SpringSourceAnalyzer().analyze(javaRoot);

        assertTrue(analysis.endpoints().size() >= 15, () -> "Expected sample endpoints, got " + analysis.endpoints().size());
        assertTrue(analysis.endpoints().stream().anyMatch(endpoint -> endpoint.httpMethod().equals("POST")
                && endpoint.path().equals("/api/projects")));
        assertTrue(analysis.endpoints().stream().anyMatch(endpoint -> endpoint.httpMethod().equals("DELETE")
                && endpoint.path().contains("/api/projects/")));
        assertTrue(analysis.symbols().stream().anyMatch(symbol -> symbol.kind().equals("APPLICATION_SERVICE")
                && symbol.qualifiedName().endsWith("ProjectService")));
        assertTrue(analysis.errorResponses().size() >= 3);
        assertTrue(analysis.errorResponses().stream().allMatch(ErrorResponseDescriptor::globalAdvice));
        assertTrue(analysis.errorResponses().stream().anyMatch(error -> error.status().equals("NOT_FOUND")
                && error.responseType().equals("ApiErrorResponse")
                && error.exceptionTypes().contains("ResourceNotFoundException")));
        assertTrue(analysis.errorResponses().stream().anyMatch(error -> error.status().equals("BAD_REQUEST")
                && error.handlerMethod().equals("validation")));
    }
}
