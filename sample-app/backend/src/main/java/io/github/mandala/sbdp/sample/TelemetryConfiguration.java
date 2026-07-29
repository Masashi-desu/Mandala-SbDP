package io.github.mandala.sbdp.sample;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TelemetryConfiguration {
    @Bean(destroyMethod = "")
    OpenTelemetry openTelemetry(
            @Value("${mandala.tracing.export.enabled:true}") boolean exportEnabled,
            @Value("${mandala.tracing.export.endpoint:http://localhost:4318/v1/traces}") String endpoint,
            @Value("${mandala.tracing.use-global:false}") boolean useGlobal) {
        // The Java agent owns the global SDK and the HTTP/JDBC parent context. Reusing it is what
        // joins the starter's service/DAO spans into one end-to-end trace.
        if (useGlobal) return GlobalOpenTelemetry.get();
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), "mandala-sample-backend",
                AttributeKey.stringKey("mandala.application"), "sample-app")));
        SdkTracerProviderBuilderFactory builder = new SdkTracerProviderBuilderFactory(resource);
        if (exportEnabled) {
            OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .setTimeout(Duration.ofSeconds(5))
                    .build();
            builder.add(BatchSpanProcessor.builder(exporter).build());
        }
        SdkTracerProvider provider = builder.build();
        return OpenTelemetrySdk.builder().setTracerProvider(provider).build();
    }

    /** Keeps the SDK builder assembly explicit and testable without exporting when disabled. */
    private static final class SdkTracerProviderBuilderFactory {
        private final io.opentelemetry.sdk.trace.SdkTracerProviderBuilder delegate;

        private SdkTracerProviderBuilderFactory(Resource resource) {
            this.delegate = SdkTracerProvider.builder().setResource(resource);
        }

        private void add(BatchSpanProcessor processor) {
            delegate.addSpanProcessor(processor);
        }

        private SdkTracerProvider build() {
            return delegate.build();
        }
    }
}
