package websocket;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool {

    private final String serverHost;
    private final int serverPort;
    private final Map<String, PooledWebSocketClient> connections;
    private final AtomicInteger connectionCount;
    private final AtomicInteger reconnectCount;

    public ConnectionPool(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.connections = new ConcurrentHashMap<>();
        this.connectionCount = new AtomicInteger(0);
        this.reconnectCount = new AtomicInteger(0);
    }

    public PooledWebSocketClient getConnection(String roomId) throws Exception {
        String key = roomId;

        PooledWebSocketClient client = connections.get(key);

        if (client == null || !client.isReady()) {
            if (client != null) {
                reconnectCount.incrementAndGet();
                client.close();
            }
            client = createConnection(roomId);
            connections.put(key, client);
        }
        return client;
    }

    private PooledWebSocketClient createConnection(String roomId) throws Exception {
        URI serverUri = new URI("ws://" + serverHost + ":" + serverPort + "/chat/" + roomId);
        PooledWebSocketClient client = new PooledWebSocketClient(serverUri, roomId);

        client.connect();
        connectionCount.incrementAndGet();

        if (!client.waitForConnection(5, TimeUnit.SECONDS)) {
            throw new Exception("Connection timeout for room " + roomId);
        }
        return client;
    }

    public void closeAll() {
        for (PooledWebSocketClient client : connections.values()) {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        connections.clear();
    }

    public int getConnectionCount() {
        return connectionCount.get();
    }

    public int getReconnectCount() {
        return reconnectCount.get();
    }

    public int getActiveConnectionCount() {
        return connections.size();
    }
}


