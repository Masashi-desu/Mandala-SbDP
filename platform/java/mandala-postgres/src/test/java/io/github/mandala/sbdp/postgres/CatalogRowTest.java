package io.github.mandala.sbdp.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogRowTest {
    @Test
    void convertsJdbcAndTextArrayRepresentations() throws Exception {
        CatalogRow row = new CatalogRow(Map.of(
                "objects", new Object[] {"owner_id", "project,id"},
                "text", "{owner_id,\"project,id\",NULL}"));

        assertEquals(List.of("owner_id", "project,id"), row.strings("objects"));
        assertEquals(List.of("owner_id", "project,id"), row.strings("text"));
    }
}
