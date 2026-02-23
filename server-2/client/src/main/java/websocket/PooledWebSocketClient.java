package websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PooledWebSocketClient extends WebSocketClient {
    private final String roomId;
    private final CountDownLatch connectLatch;
    private final AtomicBoolean isReady;
    private final AtomicLong messagesSent;
    private final AtomicLong messagesReceived;

    public PooledWebSocketClient(URI serverUri, String roomId) {
        super(serverUri);
        this.roomId = roomId;
        this.connectLatch = new CountDownLatch(1);
        this.isReady = new AtomicBoolean(false);
        this.messagesSent = new AtomicLong(0);
        this.messagesReceived = new AtomicLong(0);

        setConnectionLostTimeout(10);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        isReady.set(true);
        connectLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        messagesReceived.incrementAndGet();
        System.out.println("Echoed message received: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isReady.set(false);
    }

    @Override
    public void onError(Exception ex) {
        isReady.set(false);
        connectLatch.countDown();
    }

    public boolean waitForConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return connectLatch.await(timeout, unit);
    }

    public boolean isReady() {
        return isReady.get() && isOpen();
    }

    public String getRoomId() {
        return roomId;
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public long getMessagesSent() {
        return messagesSent.get();
    }

    public long getMessagesReceived() {
        return messagesReceived.get();
    }
}
