package io.github.mandala.sbdp.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiAnalyzerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void convertsYamlOperationsAndDereferencesSchemas() throws Exception {
        Path openApi = temporaryDirectory.resolve("openapi.yaml");
        Files.writeString(openApi, """
                openapi: 3.1.0
                paths:
                  /api/projects/{id}:
                    parameters:
                      - name: id
                        in: path
                        required: true
                        schema: { type: integer, format: int64 }
                    get:
                      operationId: getProject
                      summary: Get a project
                      responses:
                        '200':
                          description: found
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/Project'
                        '404':
                          description: absent
                components:
                  schemas:
                    Project:
                      type: object
                      required: [id, name]
                      properties:
                        id: { type: integer }
                        name: { type: string }
                """);

        EndpointDiscovery discovery = new OpenApiAnalyzer().analyze(openApi);

        assertEquals(1, discovery.endpoints().size());
        EndpointDescriptor endpoint = discovery.endpoints().getFirst();
        assertEquals("endpoint:GET:/api/projects/{id}", endpoint.stableId());
        assertEquals("Project", endpoint.responses().getFirst().type());
        assertTrue(endpoint.responses().stream().anyMatch(response -> response.status().equals("404")));
        assertEquals(ParameterLocation.PATH, endpoint.parameters().getFirst().location());
    }
}
