package config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties at startup and exposes typed getters.
 * Every other class takes an AppConfig in its constructor.
 */
public class AppConfig {

    private final Properties props;

    public AppConfig(String filePath) throws IOException {
        props = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            props.load(fis);
        }
    }

    public AppConfig() throws IOException {
        props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) throw new IOException("config.properties not found on classpath");
            props.load(is);
        }
    }

    // RabbitMQ
    public String getRabbitMQHost() {
        return props.getProperty("rabbitmq.host", "localhost");
    }

    public int getRabbitMQPort() {
        return Integer.parseInt(props.getProperty("rabbitmq.port", "5672"));
    }

    public String getRabbitMQUsername() {
        return props.getProperty("rabbitmq.username", "admin");
    }

    public String getRabbitMQPassword() {
        return props.getProperty("rabbitmq.password", "rabbitmq");
    }

    public String getRabbitMQVirtualHost() {
        return props.getProperty("rabbitmq.virtualhost", "/");
    }

    public long getRabbitMQReconnectDelayMs() {
        return Long.parseLong(props.getProperty("rabbitmq.reconnect.delay.ms", "5000"));
    }

    // Redis

    public String getRedisHost() {
        return props.getProperty("redis.host", "localhost");
    }

    public int getRedisPort() {
        return Integer.parseInt(props.getProperty("redis.port", "6379"));
    }

    public String getRedisPassword() {
        return props.getProperty("redis.password", "");
    }

    public int getDedupTtlSeconds() {
        return Integer.parseInt(props.getProperty("redis.dedup.ttl.seconds", "86400"));
    }

    // Part 1 Broadcast Callback

    /**
     * The URL of Part 1's internal broadcast endpoint.
     * Part 2 POSTs to this URL to trigger broadcasts to WebSocket clients.
     * Example: http://10.0.1.5:8081/internal/broadcast
     */
    public String getPart1BroadcastUrl() {
        return props.getProperty("part1.broadcast.url", "http://localhost:8081/internal/broadcast");
    }

    // Consumer Pool

    public int getConsumerThreadCount() {
        return Integer.parseInt(props.getProperty("consumer.thread.count", "4"));
    }

    // Health Check

    public int getHealthCheckPort() {
        return Integer.parseInt(props.getProperty("healthcheck.port", "8082"));
    }

    // Message Processing

    public int getMessageRetryMax() {
        return Integer.parseInt(props.getProperty("message.retry.max", "3"));
    }

    public long getMessageRetryDelayMs() {
        return Long.parseLong(props.getProperty("message.retry.delay.ms", "500"));
    }
}