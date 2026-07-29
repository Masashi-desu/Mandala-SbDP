package io.github.mandala.sbdp.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActuatorMappingsAnalyzerTest {
    @Test
    void readsBootThreeDispatcherMappingAndReconcilesTemplateNames() throws Exception {
        String json = """
                {
                  "contexts": {
                    "application": {
                      "mappings": {
                        "dispatcherServlets": {
                          "dispatcherServlet": [{
                            "handlerMethod": {
                              "className": "com.example.ProjectController",
                              "name": "get",
                              "descriptor": "(J)LProject;"
                            },
                            "requestMappingConditions": {
                              "methods": ["GET"],
                              "patterns": ["/api/projects/{projectId}"],
                              "produces": [{"mediaType": "application/json", "negated": false}]
                            }
                          }]
                        }
                      }
                    }
                  }
                }
                """;
        EndpointDiscovery actuator = new ActuatorMappingsAnalyzer()
                .analyze(new ObjectMapper().readTree(json), null);
        EndpointDescriptor openapi = new EndpointDescriptor(
                "endpoint:GET:/api/projects/{id}",
                "GET",
                "/api/projects/{id}",
                "",
                "getProject",
                java.util.Set.of(),
                java.util.Set.of("application/json"),
                List.of(),
                "",
                List.of(new EndpointResponse("200", "Project", "application/json", "")),
                "getProject",
                "Get project",
                "",
                EndpointSource.OPENAPI,
                null,
                java.util.Map.of());

        List<EndpointDescriptor> declarations = new ArrayList<>(actuator.endpoints());
        declarations.add(openapi);
        List<ReconciledEndpoint> reconciled = new EndpointReconciler().reconcile(declarations);

        assertEquals(1, reconciled.size());
        assertEquals("com.example.ProjectController", reconciled.getFirst().canonical().controllerClass());
        assertEquals(2, reconciled.getFirst().declarationSources().size());
    }
}
