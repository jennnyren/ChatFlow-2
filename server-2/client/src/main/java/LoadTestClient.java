import generator.MessageGenerator;
import metrics.PerformanceMetrics;
import model.MessageRound;
import websocket.ConnectionPool;
import worker.RetryWorker;
import worker.SenderWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTestClient {
    private static final int TOTAL_MESSAGES = 500000;
    private static final int WARMUP_THREADS = 32;
    private static final int WARMUP_MESSAGES_PER_THREAD = 1000;
    private static final int MAIN_THREADS = 64;
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) throws Exception {
        System.out.println("----------------------------------------");
        System.out.println("WebSocket Load Test Client Starting");
        System.out.println("Target: (around)" + TOTAL_MESSAGES + " messages");
        System.out.println("Server: ws://" + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println("----------------------------------------\n");

        //performLittlesLawAnalysis();

        LoadTestClient client = new LoadTestClient();
        client.runWarmupPhase();

        //client.runMainPhase();
    }

    private static void performLittlesLawAnalysis() throws Exception {
        System.out.println("Performing Little's Law Analysis:");
        ConnectionPool testPool = new ConnectionPool(SERVER_HOST, SERVER_PORT);
        long[] samples = new long[10];

        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            var client = testPool.getConnection("1");
            client.send("{\"userId\":\"1\",\"username\":\"test\",\"message\":\"test\"," +
                    "\"timestamp\":\"2026-01-31T00:00:00Z\",\"messageType\":\"TEXT\"}");
            Thread.sleep(10); // Wait for response
            long end = System.nanoTime();
            samples[i] = end - start;
        }

        testPool.closeAll();

        long avgRttNs = 0;
        for (long sample : samples) {
            avgRttNs += sample;
        }
        avgRttNs /= samples.length;
        double avgRttMs = avgRttNs / 1000000.0;

        BlockingQueue<MessageRound> sampleQueue = new LinkedBlockingQueue<>();
        MessageGenerator sampleGen = new MessageGenerator(sampleQueue, TOTAL_MESSAGES);
        Thread sampleThread = new Thread(sampleGen);
        sampleThread.start();
        sampleThread.join();

        int totalRounds = sampleQueue.size();
        int concurrentConnections = MAIN_THREADS;
        double predictedThroughput = (concurrentConnections / (avgRttMs / 1000.0));

        System.out.println("----------------------------------------");
        System.out.println("Average RTT: " + String.format("%.2f", avgRttMs) + " ms");
        System.out.println("Total rounds: " + totalRounds);
        System.out.println("Total messages: " + TOTAL_MESSAGES);
        System.out.println("Avg messages per round: " + String.format("%.2f", (double) TOTAL_MESSAGES / totalRounds));
        System.out.println("Concurrent connections: " + concurrentConnections);
        System.out.println("Predicted throughput: " + String.format("%.2f", predictedThroughput) + " rounds/sec");
        System.out.println("Predicted time for " + totalRounds + " rounds: " +
                String.format("%.2f", totalRounds / predictedThroughput) + " seconds");
        System.out.println("----------------------------------------\n");
    }

    private void runWarmupPhase() throws Exception {
        System.out.println("WARMUP PHASE");
        long startTime = System.currentTimeMillis();
        int warmupTotal = WARMUP_THREADS * WARMUP_MESSAGES_PER_THREAD;
        BlockingQueue<MessageRound> warmupQueue = new LinkedBlockingQueue<>(10000);
        ConnectionPool warmupPool = new ConnectionPool(SERVER_HOST, SERVER_PORT);
        String[] roomIds = new String[20]; // Assuming rooms 1-20
        for (int i = 0; i < 20; i++) {
            roomIds[i] = String.valueOf(i + 1);
        }

        AtomicInteger warmupSuccess = new AtomicInteger(0);
        AtomicInteger warmupFailure = new AtomicInteger(0);

        MessageGenerator warmupGen = new MessageGenerator(warmupQueue, warmupTotal);
        Thread genThread = new Thread(warmupGen);
        genThread.start();
        genThread.join();

        int totalRounds = warmupQueue.size();
        int roundsPerThread = (totalRounds + WARMUP_THREADS - 1) / WARMUP_THREADS;

        ExecutorService warmupExecutor = Executors.newFixedThreadPool(WARMUP_THREADS);
        List<Future<?>> warmupFutures = new ArrayList<>();

        BlockingQueue<MessageRound> warmupRetryQueue = new LinkedBlockingQueue<>();

        for (int i = 0; i < WARMUP_THREADS; i++) {
            SenderWorker worker = new SenderWorker(
                    warmupQueue, warmupRetryQueue, warmupPool,
                    warmupSuccess, warmupFailure, roundsPerThread
            );
            warmupFutures.add(warmupExecutor.submit(worker));
        }

        for (Future<?> future : warmupFutures) {
            try {
                future.get(10, TimeUnit.SECONDS); // 10 second timeout per worker
            } catch (TimeoutException e) {
                System.out.println("Worker timed out!");
                future.cancel(true);
            }
        }

        warmupExecutor.shutdown();
        warmupExecutor.awaitTermination(30, TimeUnit.SECONDS);
        warmupPool.closeAll();

        long warmupDuration = System.currentTimeMillis() - startTime;
        double warmupThroughput = (warmupSuccess.get() * 1000.0) / warmupDuration;

        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setSuccessfulMessages(warmupSuccess.get());
        metrics.setFailedMessages(warmupFailure.get());
        metrics.setTotalRuntimeMs(warmupDuration);
        metrics.setThroughput(warmupThroughput);
        metrics.setConnectionCount(warmupPool.getConnectionCount());
        metrics.setReconnectCount(warmupPool.getReconnectCount());
        metrics.setActiveConnections(warmupPool.getActiveConnectionCount());
        metrics.printReport();
    }

    private void runMainPhase() throws Exception {
        System.out.println("MAIN PHASE");

        long startTime = System.currentTimeMillis();

        BlockingQueue<MessageRound> roundQueue = new LinkedBlockingQueue<>(200000);
        BlockingQueue<MessageRound> retryQueue = new LinkedBlockingQueue<>(5000);
        ConnectionPool connectionPool = new ConnectionPool(SERVER_HOST, SERVER_PORT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        MessageGenerator generator = new MessageGenerator(roundQueue, TOTAL_MESSAGES);
        Thread generatorThread = new Thread(generator);
        generatorThread.start();

        /**
         //this logic does not work
        int totalRounds = roundQueue.size();
        int roundsPerThread = (totalRounds + MAIN_THREADS - 1) / MAIN_THREADS;
         **/

        int avgMessagesPerRound = 6; // 1 JOIN + ~4 TEXT + 1 LEAVE
        int totalRounds = TOTAL_MESSAGES / avgMessagesPerRound;
        int roundsPerThread = totalRounds / MAIN_THREADS;

        ExecutorService senderExecutor = Executors.newFixedThreadPool(MAIN_THREADS);
        List<SenderWorker> workers = new ArrayList<>();

        for (int i = 0; i < MAIN_THREADS; i++) {
            SenderWorker worker = new SenderWorker(
                    roundQueue, retryQueue, connectionPool,
                    successCount, failureCount, roundsPerThread
            );
            workers.add(worker);
            senderExecutor.submit(worker);
        }

        ExecutorService retryExecutor = Executors.newFixedThreadPool(4);
        List<RetryWorker> retryWorkers = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            RetryWorker retryWorker = new RetryWorker(
                    retryQueue, connectionPool, successCount, failureCount
            );
            retryWorkers.add(retryWorker);
            retryExecutor.submit(retryWorker);
        }

        generatorThread.join();

        int lastReported = 0;
        while (successCount.get() + failureCount.get() < TOTAL_MESSAGES) {
            Thread.sleep(1000);
            int current = successCount.get() + failureCount.get();
            if (current - lastReported >= 50000) {
                System.out.println("Progress: " + current + " / ~" + TOTAL_MESSAGES + " messages");
                lastReported = current;
            }

            // Check if we're close enough (within 5%)
            if (current >= TOTAL_MESSAGES * 0.95 && roundQueue.isEmpty() && retryQueue.isEmpty()) {
                System.out.println("Queue empty, wrapping up...");
                Thread.sleep(2000);
                break;
            }
        }

        workers.forEach(SenderWorker::shutdown);
        retryWorkers.forEach(RetryWorker::shutdown);

        senderExecutor.shutdown();
        retryExecutor.shutdown();
        senderExecutor.awaitTermination(30, TimeUnit.SECONDS);
        retryExecutor.awaitTermination(30, TimeUnit.SECONDS);

        long totalRuntime = System.currentTimeMillis() - startTime;
        double throughput = (successCount.get() * 1000.0) / totalRuntime;

        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setSuccessfulMessages(successCount.get());
        metrics.setFailedMessages(failureCount.get());
        metrics.setTotalRuntimeMs(totalRuntime);
        metrics.setThroughput(throughput);
        metrics.setConnectionCount(connectionPool.getConnectionCount());
        metrics.setReconnectCount(connectionPool.getReconnectCount());
        metrics.setActiveConnections(connectionPool.getActiveConnectionCount());
        metrics.printReport();

        connectionPool.closeAll();
    }
}
