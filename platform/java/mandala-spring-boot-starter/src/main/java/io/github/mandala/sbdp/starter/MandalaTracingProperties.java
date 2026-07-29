package io.github.mandala.sbdp.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mandala.tracing")
public class MandalaTracingProperties {
    /** Enables application-service and Doma DAO spans. */
    private boolean enabled = true;

    /** OpenTelemetry instrumentation scope used by the starter. */
    private String instrumentationName = "io.github.mandala.sbdp";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInstrumentationName() {
        return instrumentationName;
    }

    public void setInstrumentationName(String instrumentationName) {
        this.instrumentationName = instrumentationName;
    }
}
