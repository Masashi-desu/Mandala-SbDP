package io.github.mandala.sbdp.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MandalaCliTest {
    @Test
    void serveHelpDocumentsThePublishedRootWithoutOpeningARepository() {
        assertEquals(0, MandalaCli.execute("serve", "--help"));
    }
}
