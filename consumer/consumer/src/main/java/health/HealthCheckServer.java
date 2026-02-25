package health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import consumer.ConsumerPool;
import model.ConsumerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lightweight HTTP health check server for Part 2.
 * Runs on port 8082 (Part 1 already uses 8080 and 8081).
 *
 * GET /health → full JSON stats
 * GET /ready  → 200 if all threads healthy, 503 if not
 */
public class HealthCheckServer {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckServer.class);

    private final int port;
    private final ConsumerPool consumerPool;
    private HttpServer httpServer;

    public HealthCheckServer(int port, ConsumerPool consumerPool) {
        this.port = port;
        this.consumerPool = consumerPool;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/health", this::handleHealth);
        httpServer.createContext("/ready", this::handleReady);
        httpServer.start();
        log.info("Health check server started on port {}", port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        List<ConsumerMetrics> threadMetrics = consumerPool.getAllMetrics();
        boolean allHealthy = threadMetrics.stream().allMatch(ConsumerMetrics::isHealthy);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"status\": \"").append(allHealthy ? "UP" : "DEGRADED").append("\",\n");
        sb.append("  \"threadCount\": ").append(threadMetrics.size()).append(",\n");
        sb.append("  \"threads\": [\n");

        for (int i = 0; i < threadMetrics.size(); i++) {
           ConsumerMetrics m = threadMetrics.get(i);
            sb.append("    {\n");
            sb.append("      \"threadId\": \"").append(m.getThreadId()).append("\",\n");
            sb.append("      \"healthy\": ").append(m.isHealthy()).append(",\n");
            sb.append("      \"messagesProcessed\": ").append(m.getMessagesProcessed()).append(",\n");
            sb.append("      \"duplicatesSkipped\": ").append(m.getDuplicatesSkipped()).append(",\n");
            sb.append("      \"failures\": ").append(m.getMessagesFailedAllRetries()).append(",\n");
            sb.append("      \"secondsSinceLastHeartbeat\": ").append(m.secondsSinceLastHeartbeat()).append("\n");
            sb.append("    }");
            if (i < threadMetrics.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n}");

        sendResponse(exchange, 200, sb.toString());
    }

    private void handleReady(HttpExchange exchange) throws IOException {
        List<ConsumerMetrics> metrics = consumerPool.getAllMetrics();
        boolean allHealthy = metrics.stream().allMatch(ConsumerMetrics::isHealthy);
        sendResponse(exchange, allHealthy ? 200 : 503,
                "{\"ready\": " + allHealthy + "}");
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}