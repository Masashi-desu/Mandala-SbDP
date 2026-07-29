package io.github.mandala.sbdp.core;

import java.time.Duration;

public record AdapterRun(String adapterName, String adapterVersion, AdapterRunStatus status, Duration duration) {
}
