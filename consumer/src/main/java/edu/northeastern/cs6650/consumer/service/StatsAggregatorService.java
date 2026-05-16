package edu.northeastern.cs6650.consumer.service;

import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StatsAggregatorService {

  private static final Logger log = LoggerFactory.getLogger(StatsAggregatorService.class);

  @Value("${stats.threads:1}") private int threads;
  @Value("${stats.queue-capacity:50000}") private int queueCapacity;

  private BlockingQueue<QueueMessage> queue;
  private ExecutorService pool;

  private final LongAdder totalMessages = new LongAdder();
  private final ConcurrentHashMap<String, LongAdder> messagesByRoom = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, LongAdder> messagesByUser = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, LongAdder> messagesPerMinute = new ConcurrentHashMap<>();

  private final AtomicLong dropped = new AtomicLong(0);

  @PostConstruct
  public void start() {
    queue = new LinkedBlockingQueue<>(queueCapacity);
    pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(this::runLoop);
    }
    log.info("[STATS_INIT] threads={} queueCap={}", threads, queueCapacity);
  }

  public boolean enqueue(QueueMessage msg) {
    boolean ok = queue.offer(msg);
    if (!ok) dropped.incrementAndGet();
    return ok;
  }

  private void runLoop() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        QueueMessage msg = queue.poll(250, TimeUnit.MILLISECONDS);
        if (msg == null) continue;
        totalMessages.increment();
        messagesByRoom.computeIfAbsent(msg.getRoomId(), k -> new LongAdder()).increment();
        messagesByUser.computeIfAbsent(msg.getUserId(), k -> new LongAdder()).increment();
        long minuteBucket = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond();
        messagesPerMinute.computeIfAbsent(minuteBucket, k -> new LongAdder()).increment();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public long getTotalMessages() { return totalMessages.sum(); }
  public Map<String, LongAdder> getMessagesByRoom() { return messagesByRoom; }
  public Map<String, LongAdder> getMessagesByUser() { return messagesByUser; }
  public Map<Long, LongAdder> getMessagesPerMinute() { return messagesPerMinute; }
  public long getDropped() { return dropped.get(); }
  public int getQueueDepth() { return queue.size(); }

  @PreDestroy
  public void shutdown() throws Exception {
    if (pool != null) {
      pool.shutdown();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
