package io.github.mandala.sbdp.starter;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds safe scenario and stable endpoint identifiers to the Java agent's current server span. */
public final class MandalaHttpServerFilter extends OncePerRequestFilter {
    public static final String FLOW_HEADER = "X-Mandala-Flow-Id";
    private static final Pattern SAFE_FLOW = Pattern.compile("[A-Za-z0-9._:-]{1,160}");
    private static final Pattern UUID = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Span current = Span.current();
        current.setAttribute("mandala.layer", "http_server");
        current.setAttribute("mandala.endpoint.id", "endpoint:" + request.getMethod().toUpperCase(Locale.ROOT)
                + ":" + normalizePath(request.getRequestURI()));
        String flow = request.getHeader(FLOW_HEADER);
        if (flow != null && SAFE_FLOW.matcher(flow).matches()) current.setAttribute("mandala.flow.id", flow);
        chain.doFilter(request, response);
    }

    static String normalizePath(String input) {
        String path = input == null || input.isBlank() ? "/" : input.replaceAll("/{2,}", "/");
        if (!path.startsWith("/")) path = "/" + path;
        String[] segments = path.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            if (segments[index].matches("\\d+") || UUID.matcher(segments[index]).matches()) segments[index] = "{id}";
        }
        return String.join("/", segments);
    }
}
