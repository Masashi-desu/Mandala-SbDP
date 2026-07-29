package io.github.mandala.sbdp.spring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointReconcilerTest {
    @Test
    void treatsSpringEnumAndOpenApiNumericSuccessStatusesAsEquivalent() {
        ReconciledEndpoint endpoint = new EndpointReconciler().reconcile(List.of(
                endpoint(EndpointSource.JAVA_SOURCE, "CREATED"),
                endpoint(EndpointSource.OPENAPI, "201"))).getFirst();

        assertTrue(endpoint.conflicts().isEmpty(), endpoint.conflicts().toString());
    }

    @Test
    void reportsContradictoryJavaAndOpenApiSuccessStatuses() {
        ReconciledEndpoint endpoint = new EndpointReconciler().reconcile(List.of(
                endpoint(EndpointSource.JAVA_SOURCE, "200"),
                endpoint(EndpointSource.OPENAPI, "201"))).getFirst();

        assertFalse(endpoint.conflicts().isEmpty());
        assertTrue(endpoint.conflicts().stream()
                .anyMatch(conflict -> conflict.contains("Successful response status declarations disagree")));
    }

    @Test
    void selectsTheSameCanonicalHandlerRegardlessOfActuatorDeclarationOrder() {
        EndpointDescriptor json = actuatorError("error",
                "(Ljakarta/servlet/http/HttpServletRequest;)Lorg/springframework/http/ResponseEntity;");
        EndpointDescriptor html = actuatorError("errorHtml",
                "(Ljakarta/servlet/http/HttpServletRequest;Ljakarta/servlet/http/HttpServletResponse;)"
                        + "Lorg/springframework/web/servlet/ModelAndView;");

        ReconciledEndpoint first = new EndpointReconciler().reconcile(List.of(html, json)).getFirst();
        ReconciledEndpoint second = new EndpointReconciler().reconcile(List.of(json, html)).getFirst();

        assertEquals(first.canonical(), second.canonical());
        assertEquals("error", first.canonical().handlerMethod());
        assertEquals(Set.of("application/json", "text/html"), first.canonical().produces());
        assertEquals(List.of("application/json", "text/html"), List.copyOf(first.canonical().produces()));
    }

    private EndpointDescriptor actuatorError(String handler, String descriptor) {
        return new EndpointDescriptor(
                EndpointDescriptor.stableId("ANY", "/error"),
                "ANY", "/error", "example.BasicErrorController", handler,
                Set.of(), Set.of(handler.equals("error") ? "application/json" : "text/html"),
                List.of(), "", List.of(), "", "", "", EndpointSource.ACTUATOR, null,
                Map.of("handlerDescriptor", descriptor));
    }

    private EndpointDescriptor endpoint(EndpointSource source, String status) {
        return new EndpointDescriptor(
                EndpointDescriptor.stableId("POST", "/projects"),
                "POST", "/projects", "example.ProjectController", "create",
                Set.of("application/json"), Set.of("application/json"), List.of(), "CreateProject",
                List.of(new EndpointResponse(status, "Project", "application/json", "")),
                "createProject", "", "", source, null, Map.of());
    }
}
