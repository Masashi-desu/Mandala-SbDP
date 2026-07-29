package io.github.mandala.sbdp.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MandalaTracingAutoConfigurationTest {
    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();

    @AfterEach
    void close() {
        tracerProvider.close();
        exporter.close();
    }

    @Test
    void recordsApplicationServiceAndDaoSpansWithStableMandalaAttributes() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MandalaTracingAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withBean(OpenTelemetry.class, () -> OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerProvider)
                        .build())
                .run(context -> {
                    context.getBean(SampleService.class).execute();
                    context.getBean(SampleService.class).execute("value");
                    context.getBean(SampleDao.class).find();
                    context.getBean(SampleDao.class).find(1L);

                    List<SpanData> spans = exporter.getFinishedSpanItems();
                    assertThat(spans).hasSize(4);
                    assertThat(spans)
                            .extracting(span -> span.getAttributes().get(
                                    AttributeKey.stringKey("mandala.layer")))
                            .containsExactlyInAnyOrder("application_service", "application_service", "doma_dao", "doma_dao");
                    assertThat(spans)
                            .extracting(span -> span.getAttributes().get(
                                    AttributeKey.stringKey("mandala.stable_id")))
                            .containsExactlyInAnyOrder(
                                    "java:" + SampleService.class.getName() + "#execute()",
                                    "java:" + SampleService.class.getName() + "#execute(String)",
                                    "dao:" + SampleDao.class.getName() + "#find()",
                                    "dao:" + SampleDao.class.getName() + "#find(long)");
                });
    }

    @Test
    void marksFailedServiceSpanAsErrorAndRethrows() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MandalaTracingAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withBean(OpenTelemetry.class, () -> OpenTelemetrySdk.builder()
                        .setTracerProvider(tracerProvider)
                        .build())
                .run(context -> {
                    assertThatThrownBy(() -> context.getBean(SampleService.class).fail())
                            .isInstanceOf(IllegalStateException.class);
                    assertThat(exporter.getFinishedSpanItems()).singleElement()
                            .extracting(SpanData::getStatus)
                            .satisfies(status -> assertThat(status.getStatusCode().name()).isEqualTo("ERROR"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean
        SampleService sampleService() {
            return new SampleService();
        }

        @Bean
        SampleDao sampleDao() {
            return new SampleDao();
        }
    }

    @MandalaApplicationService
    static class SampleService {
        String execute() {
            return "ok";
        }

        String execute(String value) {
            return value;
        }

        String fail() {
            throw new IllegalStateException("boom");
        }
    }

    static class SampleDao {
        public String find() {
            return "row";
        }

        public String find(long id) {
            return "row-" + id;
        }
    }
}
