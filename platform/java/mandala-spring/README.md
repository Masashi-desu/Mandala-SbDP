# mandala-spring

Spring MVC / WebFlux の宣言を、Spring本体をクラスパスへロードせずに解析するAdapterです。

```java
SpringSourceAnalysis source = new SpringSourceAnalyzer().analyze(Path.of("src/main/java"));
EndpointDiscovery openapi = new OpenApiAnalyzer().analyze(Path.of("build/openapi.yaml"));
EndpointDiscovery runtime = new ActuatorMappingsAnalyzer().analyze(Path.of("mappings.json"));

List<ReconciledEndpoint> endpoints = new EndpointReconciler().reconcile(Stream.of(
        source.endpoints(), openapi.endpoints(), runtime.endpoints())
    .flatMap(Collection::stream)
    .toList());
```

Java Sourceからclass/method mapping、HTTP method/path、consumes/produces、request binding、Bean Validation、response status/type、`@ExceptionHandler`、Javadoc先頭説明、Controller/Application Service境界を取得します。OpenAPI 3のJSON/YAMLとSpring Boot Actuator `mappings` JSONは同じ `EndpointDescriptor` へ正規化され、method/path templateをキーに照合されます。照合時の不一致は `ReconciledEndpoint.conflicts` へ残ります。
