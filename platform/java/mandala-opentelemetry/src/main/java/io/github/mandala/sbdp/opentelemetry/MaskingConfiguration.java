package io.github.mandala.sbdp.opentelemetry;

import java.util.List;
import java.util.Set;

public record MaskingConfiguration(
        Set<String> sensitiveKeys,
        List<String> sensitiveKeyFragments,
        String replacement,
        boolean maskSqlLiterals) {
    public MaskingConfiguration {
        sensitiveKeys = Set.copyOf(sensitiveKeys == null ? Set.of() : sensitiveKeys);
        sensitiveKeyFragments = List.copyOf(sensitiveKeyFragments == null ? List.of() : sensitiveKeyFragments);
        replacement = replacement == null ? "[REDACTED]" : replacement;
    }

    public static MaskingConfiguration secureDefaults() {
        return new MaskingConfiguration(
                Set.of(
                        "http.request.header.authorization",
                        "http.request.header.cookie",
                        "http.response.header.set-cookie",
                        "url.query",
                        "db.connection_string",
                        "server.address",
                        "user.id",
                        "user.name"),
                List.of(
                        "password", "passwd", "secret", "authorization", "cookie", "sessionid",
                        "accesstoken", "refreshtoken", "apikey", "credential", "privatekey",
                        "requestbody", "responsebody", "enduserid", "endusername", "useremail",
                        "emailaddress", "phonenumber", "clientaddress", "networkpeeraddress",
                        "exceptionmessage", "exceptionstacktrace", "errormessage"),
                "[REDACTED]",
                true);
    }
}
