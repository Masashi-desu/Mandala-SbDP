package io.github.mandala.sbdp.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class StaticFileServer {
    public void serve(Path root, String bind, int port) throws IOException, InterruptedException {
        if (!Files.isDirectory(root)) throw new IOException("Generated site does not exist: " + root);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", exchange -> handle(normalizedRoot, exchange));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1), "mandala-server-shutdown"));
        server.start();
        System.out.printf("Mandala is available at http://%s:%d/ (Ctrl+C to stop)%n", bind, port);
        new CountDownLatch(1).await();
    }

    private void handle(Path root, HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(405, -1); return; }
            String raw = exchange.getRequestURI().getPath();
            Path target = root.resolve(raw.substring(Math.min(1, raw.length()))).normalize();
            if (Files.isDirectory(target)) target = target.resolve("index.html");
            if (!target.startsWith(root) || !Files.isRegularFile(target)) { exchange.sendResponseHeaders(404, -1); return; }
            String contentType = URLConnection.guessContentTypeFromName(target.getFileName().toString());
            if (contentType == null) contentType = "application/octet-stream";
            exchange.getResponseHeaders().set("Content-Type", contentType + (contentType.startsWith("text/") || contentType.contains("json") ? "; charset=utf-8" : ""));
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'");
            long size = Files.size(target); exchange.sendResponseHeaders(200, exchange.getRequestMethod().equals("HEAD") ? -1 : size);
            if (!exchange.getRequestMethod().equals("HEAD")) Files.copy(target, exchange.getResponseBody());
        }
    }
}
