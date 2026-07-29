package io.github.mandala.sbdp.renderer;

import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class PagePaths {
    private PagePaths() {}

    public static String forNode(Node node) {
        return directory(node.type()) + "/" + slug(node.id().value()) + ".html";
    }

    public static String directory(NodeType type) {
        return switch (type) {
            case E2E_FLOW -> "e2e";
            case SCREEN, SCREEN_STATE, UI_ENTRY, UI_ACTION, SCREENSHOT -> "screens";
            case HTTP_ENDPOINT, HTTP_CLIENT_CALL, OPENAPI_OPERATION, REQUEST_SCHEMA, RESPONSE_SCHEMA -> "endpoints";
            case JAVA_CLASS, JAVA_METHOD, CONTROLLER, APPLICATION_SERVICE -> "symbols";
            case DOMA_DAO, DOMA_DAO_METHOD -> "daos";
            case SQL_STATEMENT -> "sql";
            case DB_SCHEMA, DB_TABLE, DB_COLUMN, DB_VIEW, DB_MATERIALIZED_VIEW, DB_FUNCTION, DB_TRIGGER, DB_POLICY -> "tables";
            case TRACE, SPAN -> "traces";
            case CUSTOM_HTML_SECTION -> "custom";
        };
    }

    public static String slug(String stableId) {
        String readable = stableId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (readable.length() > 70) readable = readable.substring(0, 70).replaceAll("-$", "");
        return (readable.isBlank() ? "item" : readable) + "-" + digest(stableId).substring(0, 10);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
