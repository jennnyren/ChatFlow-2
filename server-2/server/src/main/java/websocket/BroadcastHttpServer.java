package websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Internal HTTP server that Part 2 (the consumer) calls to trigger broadcasts.
 *
 * Runs on port 8081 — separate from the WebSocket server on 8080.
 * NOT exposed to the public internet — internal network only.
 *
 * Endpoint:
 *   POST /internal/broadcast
 *   Body: { "roomId": "room1", "message": "<json string of the full message>" }
 *
 * What it does:
 *   Iterates Part 1's roomMapping (WebSocket → roomId),
 *   finds all connections whose value matches the target roomId,
 *   and sends the message payload to each one.
 *
 * Why iterate instead of a reverse map?
 *   Part 1's existing roomMapping is Map<WebSocket, String> and we
 *   committed to zero structural changes to existing code. Iterating
 *   is O(n) over active connections — perfectly fine for a chat app.
 */
public class BroadcastHttpServer {

    private final int port;
    private final Map<WebSocket, String> roomMapping;
    private final ObjectMapper objectMapper;
    private HttpServer httpServer;

    public BroadcastHttpServer(int port, Map<WebSocket, String> roomMapping) {
        this.port = port;
        this.roomMapping = roomMapping;
        this.objectMapper = new ObjectMapper();
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/internal/broadcast", this::handleBroadcast);
        httpServer.start();
        System.out.println("BroadcastHttpServer started on port " + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            System.out.println("BroadcastHttpServer stopped.");
        }
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    private void handleBroadcast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        // Read request body
        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Parse JSON
        String roomId;
        String message;
        try {
            JsonNode json = objectMapper.readTree(body);
            roomId = json.path("roomId").asText();
            message = json.path("message").asText();

            if (roomId.isEmpty() || message.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"roomId and message are required\"}");
                return;
            }
        } catch (Exception e) {
            sendResponse(exchange, 400, "{\"error\":\"Invalid JSON: " + e.getMessage() + "\"}");
            return;
        }

        // Broadcast to all connections in the target room
        int sent = 0;
        int failed = 0;

        for (Map.Entry<WebSocket, String> entry : roomMapping.entrySet()) {
            if (roomId.equals(entry.getValue())) {
                WebSocket conn = entry.getKey();
                try {
                    if (conn.isOpen()) {
                        conn.send(message);
                        sent++;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to send to client in room " + roomId + ": " + e.getMessage());
                    failed++;
                }
            }
        }

        System.out.println("Broadcast to room '" + roomId + "': " + sent + " delivered, " + failed + " failed.");

        // Return 200 even if some individual sends failed — Part 2 uses this
        // to decide whether to ack the RabbitMQ message. As long as we attempted
        // the broadcast, we ack. Individual client failures are not retryable here.
        String response = String.format(
                "{\"sent\":%d,\"failed\":%d,\"roomId\":\"%s\"}", sent, failed, roomId);
        sendResponse(exchange, 200, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}