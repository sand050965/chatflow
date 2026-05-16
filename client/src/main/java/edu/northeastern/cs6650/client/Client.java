package edu.northeastern.cs6650.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.northeastern.cs6650.client.config.ClientConfig;
import edu.northeastern.cs6650.client.dto.ChatMessage;
import edu.northeastern.cs6650.client.dto.MessageType;
import edu.northeastern.cs6650.client.generator.MessageGenerator;
import edu.northeastern.cs6650.client.metric.MetricsCollector;
import edu.northeastern.cs6650.client.network.ChatWebSocketClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Client {

  private final BlockingQueue<ChatMessage> messageQueue;
  private final MetricsCollector metricsCollector;
  private final ObjectMapper objectMapper;

  public Client() {
    this.messageQueue = new LinkedBlockingQueue<>(ClientConfig.MESSAGE_QUEUE_SIZE);
    this.metricsCollector = new MetricsCollector();
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  public static void main(String[] args) {
    System.out.println("================================================");
    System.out.println("  ChatFlow Load Testing Client - Assignment 3");
    System.out.println("================================================");
    System.out.println("Server:         " + ClientConfig.SERVER_URL);
    System.out.println("Total Messages: " + ClientConfig.TOTAL_MESSAGES);
    System.out.println("Main Threads:   " + ClientConfig.MAIN_THREADS);
    System.out.println("Output File:    " + ClientConfig.CSV_FILE);
    System.out.println("================================================\n");
    new Client().run();
  }

  public void run() {
    try {
      Files.createDirectories(Paths.get("results"));
    } catch (IOException e) {
      System.err.println("✗ mkdir results: " + e.getMessage());
    }

    Thread genThread = new Thread(new MessageGenerator(messageQueue), "MessageGenerator");
    genThread.start();
    System.out.println("✓ Message generator started\n");

    Instant testStart = Instant.now();
    long startTime = System.currentTimeMillis();

    // ── Phase 1: Warmup ──────────────────────────────────────────
    System.out.println("=== Phase 1: Warmup ===");
    System.out.println("Threads:          " + ClientConfig.WARMUP_THREADS);
    System.out.println("Messages/thread:  " + ClientConfig.WARMUP_MESSAGES_PER_THREAD);

    long warmupStart = System.currentTimeMillis();
    runPhase(ClientConfig.WARMUP_THREADS, ClientConfig.WARMUP_MESSAGES_PER_THREAD, 0);
    long warmupMs = System.currentTimeMillis() - warmupStart;

    int warmupMessages = ClientConfig.WARMUP_THREADS * ClientConfig.WARMUP_MESSAGES_PER_THREAD;
    double warmupThroughput = warmupMessages * 1000.0 / warmupMs;

    System.out.printf("%nWarmup complete: %d msgs in %d ms (%.2f msg/sec)%n",
        warmupMessages, warmupMs, warmupThroughput);
    System.out.println("Connections opened:    " + metricsCollector.getConnectionCount());
    System.out.println("Connection failures:   " + metricsCollector.getConnectionFailures());

    // ── Phase 2: Main Load ───────────────────────────────────────
    int remainingMessages = ClientConfig.TOTAL_MESSAGES - warmupMessages;
    int msgsPerThread = remainingMessages / ClientConfig.MAIN_THREADS;
    int extraMessages = remainingMessages % ClientConfig.MAIN_THREADS;

    System.out.println("\n=== Phase 2: Main Load ===");
    System.out.println("Threads:        " + ClientConfig.MAIN_THREADS);
    System.out.println("Remaining msgs: " + remainingMessages);

    long mainStart = System.currentTimeMillis();
    runPhase(ClientConfig.MAIN_THREADS, msgsPerThread, extraMessages);
    long mainMs = System.currentTimeMillis() - mainStart;

    genThread.interrupt();
    long totalMs = System.currentTimeMillis() - startTime;

    metricsCollector.writeMetricsToCSV();
    metricsCollector.printDetailedStatistics(
        totalMs, warmupMs, warmupThroughput, mainMs, warmupMessages, remainingMessages);
    metricsCollector.generateThroughputData();

    Instant testEnd = Instant.now();
    fetchAndLogAnalytics(testStart, testEnd);
  }

  private void runPhase(int threadCount, int msgsPerThread, int extraMessages) {
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      final int messagesToSend = msgsPerThread + (i < extraMessages ? 1 : 0);
      executor.submit(() -> {
        try {
          sendMessages(threadId, messagesToSend);
        } finally {
          latch.countDown();
        }
      });
    }
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    executor.shutdown();
  }

  private void sendMessages(int threadId, int count) {
    ChatWebSocketClient client = null;
    int roomId = (threadId % ClientConfig.ROOM_COUNT) + 1;

    try {
      client = new ChatWebSocketClient(new URI(ClientConfig.SERVER_URL + roomId));
      if (!client.connectBlocking(ClientConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        System.err.printf("✗ Thread %d failed to connect to room %d%n", threadId, roomId);
        metricsCollector.recordConnectionFailure(); // ← 新增
        for (int i = 0; i < count; i++) {
          metricsCollector.recordFailure();
        }
        return;
      }
      metricsCollector.recordConnection();

      for (int i = 0; i < count; i++) {
        ChatMessage message = messageQueue.poll(
            ClientConfig.MESSAGE_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (message == null) {
          metricsCollector.recordFailure();
          continue;
        }
        sendMessage(client, message, message.getMessageType(), roomId);
        logProgress();
      }

    } catch (Exception e) {
      System.err.printf("✗ Thread %d error: %s%n", threadId, e.getMessage());
      metricsCollector.recordFailure();
    } finally {
      if (client != null && client.isOpen()) {
        client.close();
      }
    }
  }

  private boolean sendMessage(ChatWebSocketClient client,
                              ChatMessage message, MessageType type, int roomId) {
    ChatMessage toSend = new ChatMessage(
        message.getUserId(), message.getUsername(),
        message.getMessage(), Instant.now().toString(), type);

    for (int retry = 0; retry < ClientConfig.MAX_RETRIES; retry++) {
      try {
        if (client == null || !client.isOpen()) {
          client = new ChatWebSocketClient(new URI(ClientConfig.SERVER_URL + roomId));
          if (!client.connectBlocking(ClientConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            metricsCollector.recordConnectionFailure(); // ← 新增
            throw new Exception("Reconnection failed");
          }
          metricsCollector.recordReconnection();
          metricsCollector.recordConnection();
        }

        boolean ok = client.sendAndWait(objectMapper.writeValueAsString(toSend), 3000);
        long ts = client.getSendTimestamp();
        long latency = client.getLastLatency();

        if (ok) {
          metricsCollector.recordSuccess(roomId, type, ts, latency);
          return true;
        }
        throw new Exception("Response timeout");

      } catch (Exception e) {
        if (retry < ClientConfig.MAX_RETRIES - 1) {
          metricsCollector.recordRetry(); // ← 新增
          try {
            Thread.sleep((long) Math.pow(ClientConfig.RETRY_BACKOFF_MULTIPLIER, retry)
                * ClientConfig.RETRY_BASE_BACKOFF_MS);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            metricsCollector.recordFailure();
            return false;
          }
        }
      }
    }
    metricsCollector.recordFailure();
    return false;
  }

  private void logProgress() {
    int total = metricsCollector.getSuccessCount() + metricsCollector.getFailureCount();
    if (total % ClientConfig.PROGRESS_LOG_INTERVAL == 0) {
      System.out.println("  Progress: " + total + " / " + ClientConfig.TOTAL_MESSAGES);
    }
  }

  private void fetchAndLogAnalytics(Instant start, Instant end) {
    try {
      String url = ClientConfig.METRICS_URL
          + "?roomId=1"
          + "&userId=1"
          + "&startTime=" + urlEncode(start.toString())
          + "&endTime=" + urlEncode(end.toString())
          + "&topN=5"
          + "&limit=1000";

      HttpClient httpClient = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      System.out.println("\n============================================================");
      System.out.println("              METRICS API RESULT");
      System.out.println("============================================================");
      System.out.println("Status: " + response.statusCode());

      if (response.statusCode() == 200) {
        Object json = objectMapper.readValue(response.body(), Object.class);
        String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        System.out.println(pretty);
      } else {
        System.out.println(response.body());
      }

      Path out = Paths.get("results/metrics_api_response.json");
      Files.writeString(out, response.body());
      System.out.println("\nSaved metrics JSON to " + out);
      System.out.println("============================================================");
    } catch (Exception e) {
      System.err.println("✗ Metrics API call failed: " + e.getMessage());
    }
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
