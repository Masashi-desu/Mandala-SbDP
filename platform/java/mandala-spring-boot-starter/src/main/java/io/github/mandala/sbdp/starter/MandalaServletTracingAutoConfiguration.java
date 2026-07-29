package io.github.mandala.sbdp.starter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet-specific bridge; WebFlux consumers can use the tracing aspect without this optional classpath. */
@AutoConfiguration(after = MandalaTracingAutoConfiguration.class)
@ConditionalOnClass({HttpServletRequest.class, OncePerRequestFilter.class})
@ConditionalOnProperty(prefix = "mandala.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MandalaServletTracingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    MandalaHttpServerFilter mandalaHttpServerFilter() {
        return new MandalaHttpServerFilter();
    }
}
