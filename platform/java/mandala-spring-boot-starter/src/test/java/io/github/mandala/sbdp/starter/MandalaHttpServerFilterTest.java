package io.github.mandala.sbdp.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MandalaHttpServerFilterTest {
    @Test
    void normalizesEphemeralIdentifiersWithoutChangingBusinessPathSegments() {
        assertThat(MandalaHttpServerFilter.normalizePath("/api/projects/42/tasks/550e8400-e29b-41d4-a716-446655440000"))
                .isEqualTo("/api/projects/{id}/tasks/{id}");
        assertThat(MandalaHttpServerFilter.normalizePath("api/projects/search"))
                .isEqualTo("/api/projects/search");
    }
}
