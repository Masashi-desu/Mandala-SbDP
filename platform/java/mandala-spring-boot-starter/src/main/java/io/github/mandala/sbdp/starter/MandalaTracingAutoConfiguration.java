package io.github.mandala.sbdp.starter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@ConditionalOnClass({Tracer.class, Aspect.class})
@ConditionalOnProperty(prefix = "mandala.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MandalaTracingProperties.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class MandalaTracingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    MandalaTracingAspect mandalaTracingAspect(
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            MandalaTracingProperties properties) {
        OpenTelemetry telemetry = openTelemetryProvider.getIfAvailable(GlobalOpenTelemetry::get);
        return new MandalaTracingAspect(telemetry.getTracer(properties.getInstrumentationName()));
    }
}
