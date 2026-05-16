package edu.northeastern.cs6650.client.metric;

import edu.northeastern.cs6650.client.config.ClientConfig;
import edu.northeastern.cs6650.client.dto.MessageMetric;
import edu.northeastern.cs6650.client.dto.MessageType;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MetricsCollector {

  private final Queue<MessageMetric> metrics;
  private final Map<Integer, AtomicInteger> roomMessageCounts;
  private final Map<MessageType, AtomicInteger> typeMessageCounts;
  private final AtomicInteger successCount;
  private final AtomicInteger failureCount;
  private final AtomicInteger reconnectionCount;
  private final AtomicInteger connectionCount;
  private final AtomicInteger retryCount;
  private final AtomicInteger connectionFailures;

  public MetricsCollector() {
    this.metrics = new ConcurrentLinkedQueue<>();
    this.roomMessageCounts = new ConcurrentHashMap<>();
    this.typeMessageCounts = new ConcurrentHashMap<>();
    this.successCount = new AtomicInteger(0);
    this.failureCount = new AtomicInteger(0);
    this.reconnectionCount = new AtomicInteger(0);
    this.connectionCount = new AtomicInteger(0);
    this.retryCount = new AtomicInteger(0);
    this.connectionFailures = new AtomicInteger(0);

    for (int i = 1; i <= ClientConfig.ROOM_COUNT; i++) {
      roomMessageCounts.put(i, new AtomicInteger(0));
    }
    for (MessageType t : MessageType.values()) {
      typeMessageCounts.put(t, new AtomicInteger(0));
    }
  }

  public void recordSuccess(int roomId, MessageType type, long timestamp, long latencyMs) {
    successCount.incrementAndGet();
    roomMessageCounts.computeIfAbsent(roomId, k -> new AtomicInteger()).incrementAndGet();
    typeMessageCounts.get(type).incrementAndGet();
    metrics.add(new MessageMetric(timestamp, type, latencyMs, "SUCCESS", roomId));
  }

  public void recordFailure() {
    failureCount.incrementAndGet();
  }

  public void recordReconnection() {
    reconnectionCount.incrementAndGet();
  }

  public void recordConnection() {
    connectionCount.incrementAndGet();
  }

  public void recordRetry() {
    retryCount.incrementAndGet();
  }

  public void recordConnectionFailure() {
    connectionFailures.incrementAndGet();
  }

  public int getSuccessCount() {
    return successCount.get();
  }

  public int getFailureCount() {
    return failureCount.get();
  }

  public int getReconnectionCount() {
    return reconnectionCount.get();
  }

  public int getConnectionCount() {
    return connectionCount.get();
  }

  public int getRetryCount() {
    return retryCount.get();
  }

  public int getConnectionFailures() {
    return connectionFailures.get();
  }

  public void writeMetricsToCSV() {
    try {
      Files.createDirectories(Paths.get(ClientConfig.CSV_FILE).getParent());
      try (FileWriter w = new FileWriter(ClientConfig.CSV_FILE)) {
        w.write("timestamp,messageType,latency,statusCode,roomId\n");
        for (MessageMetric m : metrics) {
          w.write(String.format("%d,%s,%d,%s,%d\n",
              m.getTimestamp(), m.getMessageType(),
              m.getLatencyMs(), m.getStatusCode(), m.getRoomId()));
        }
      }
      System.out.println("✓ Metrics written to: " + ClientConfig.CSV_FILE);
    } catch (IOException e) {
      System.err.println("✗ CSV write failed: " + e.getMessage());
    }
  }

  public void printDetailedStatistics(long totalDurationMs,
                                      long warmupMs, double warmupThroughput,
                                      long mainMs, int warmupMessages, int mainMessages) {
    List<MessageMetric> metricsList = new ArrayList<>(metrics);
    List<Long> latencies = metricsList.stream()
        .map(MessageMetric::getLatencyMs)
        .sorted()
        .collect(Collectors.toList());

    int total = successCount.get() + failureCount.get();
    double overallThroughput = total > 0 ? successCount.get() * 1000.0 / totalDurationMs : 0;
    double mainThroughput = mainMessages > 0 ? mainMessages * 1000.0 / mainMs : 0;
    double totalSeconds = totalDurationMs / 1000.0;

    System.out.println("\n" + "=".repeat(60));
    System.out.println("                 PERFORMANCE SUMMARY");
    System.out.println("=".repeat(60));

    System.out.println("\n--- Overall Performance ---");
    System.out.printf("Successful messages:  %d%n", successCount.get());
    System.out.printf("Failed messages:      %d%n", failureCount.get());
    System.out.printf("Total connections:    %d%n", connectionCount.get());
    System.out.printf("Connection failures:  %d%n", connectionFailures.get());  // 新增
    System.out.printf("Total reconnections:  %d%n", reconnectionCount.get());
    System.out.printf("Total retries:        %d%n", retryCount.get());          // 新增
    System.out.printf("Total runtime:        %d ms (%.2f sec)%n",
        totalDurationMs, totalDurationMs / 1000.0);
    System.out.printf("Overall throughput:   %.2f msg/sec%n", overallThroughput);

    if (!latencies.isEmpty()) {
      double mean = latencies.stream().mapToLong(l -> l).average().orElse(0);
      long median = latencies.get(latencies.size() / ClientConfig.MEDIAN_DIVISOR);
      long p95 = latencies.get((int) (latencies.size() * ClientConfig.PERCENTILE_95));
      long p99 = latencies.get((int) (latencies.size() * ClientConfig.PERCENTILE_99));
      long min = latencies.get(0);
      long max = latencies.get(latencies.size() - 1);

      System.out.println("\n--- Latency Statistics (client send → server ack) ---");
      System.out.printf("Mean: %.2f ms | Median: %d ms | P95: %d ms | P99: %d ms%n",
          mean, median, p95, p99);
      System.out.printf("Min: %d ms | Max: %d ms%n", min, max);
    }

    System.out.println("\n--- Sent Message Type Distribution ---");
    System.out.println("(Generated: 90% TEXT, 5% JOIN, 5% LEAVE)");
    typeMessageCounts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
          int cnt = e.getValue().get();
          System.out.printf("  %-6s %,8d  (%.1f%%)%n",
              e.getKey(), cnt, total > 0 ? cnt * 100.0 / total : 0);
        });

    System.out.println("\n--- Throughput per Room ---");
    System.out.printf("%-10s %12s %20s%n", "Room", "Messages", "Throughput (msg/s)");
    System.out.println("-".repeat(44));
    roomMessageCounts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> {
          int cnt = e.getValue().get();
          double throughput = totalSeconds > 0 ? cnt / totalSeconds : 0;
          System.out.printf("Room %-5d %,10d %,17.2f%n", e.getKey(), cnt, throughput);
        });

    System.out.println("\n--- Phase Breakdown ---");
    System.out.println("  Warmup phase:");
    System.out.printf("    Duration:   %d ms%n", warmupMs);
    System.out.printf("    Messages:   %d%n", warmupMessages);
    System.out.printf("    Throughput: %.2f msg/sec%n", warmupThroughput);
    System.out.println("  Main phase:");
    System.out.printf("    Duration:   %d ms%n", mainMs);
    System.out.printf("    Messages:   %d%n", mainMessages);
    System.out.printf("    Throughput: %.2f msg/sec%n", mainThroughput);

    System.out.println("=".repeat(60) + "\n");
  }

  public void generateThroughputData() {
    if (metrics.isEmpty()) {
      return;
    }
    List<MessageMetric> list = new ArrayList<>(metrics);
    long startTime = list.get(0).getTimestamp();
    long bucketSize = ClientConfig.THROUGHPUT_BUCKET_SIZE_MS;
    Map<Long, Integer> map = new TreeMap<>();
    for (MessageMetric m : list) {
      long bucket = ((m.getTimestamp() - startTime) / bucketSize) * bucketSize;
      map.merge(bucket, 1, Integer::sum);
    }
    System.out.println("\n--- Throughput Over Time (10s buckets) ---");
    System.out.println("Second, Messages/sec");
    map.forEach((bucket, count) ->
        System.out.printf("%.0f, %.1f%n",
            bucket / ClientConfig.THROUGHPUT_BUCKET_TO_SECONDS,
            count / ClientConfig.THROUGHPUT_DIVISOR));
  }
}