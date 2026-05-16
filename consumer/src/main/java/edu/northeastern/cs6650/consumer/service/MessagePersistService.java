package edu.northeastern.cs6650.consumer.service;

import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import edu.northeastern.cs6650.consumer.repository.MessageWriteRepository;
import edu.northeastern.cs6650.consumer.util.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessagePersistService {

  private static final Logger log = LoggerFactory.getLogger(MessagePersistService.class);

  @Value("${db.writer.threads:8}") private int writerThreads;
  @Value("${db.writer.batch-size:500}") private int batchSize;
  @Value("${db.writer.flush-interval-ms:200}") private long flushIntervalMs;
  @Value("${db.writer.queue-capacity:50000}") private int queueCapacity;
  @Value("${db.writer.offer-timeout-ms:50}") private long offerTimeoutMs;
  @Value("${db.writer.max-retries:5}") private int maxRetries;
  @Value("${db.writer.base-backoff-ms:100}") private long baseBackoffMs;
  @Value("${db.writer.dlq-capacity:10000}") private int dlqCapacity;

  @Value("${db.writer.circuit-failure-threshold:5}") private int cbFailureThreshold;
  @Value("${db.writer.circuit-cooldown-ms:30000}") private long cbCooldownMs;
  @Value("${db.writer.circuit-half-open-max-calls:3}") private int cbHalfOpenCalls;

  private final MessageWriteRepository repository;
  private final StringRedisTemplate redisTemplate;
  private CircuitBreaker circuitBreaker;

  private BlockingQueue<QueueMessage> writeQueue;
  private BlockingQueue<QueueMessage> deadLetterQueue;
  private ExecutorService writerPool;

  private final AtomicLong enqueued = new AtomicLong(0);
  private final AtomicLong persisted = new AtomicLong(0);
  private final AtomicLong failed = new AtomicLong(0);
  private final AtomicLong dlqCount = new AtomicLong(0);

  public MessagePersistService(MessageWriteRepository repository, StringRedisTemplate redisTemplate) {
    this.repository = repository;
    this.redisTemplate = redisTemplate;
  }

  @PostConstruct
  public void start() {
    circuitBreaker = new CircuitBreaker(
        "Postgres", cbFailureThreshold, cbCooldownMs, cbHalfOpenCalls);
    writeQueue = new LinkedBlockingQueue<>(queueCapacity);
    deadLetterQueue = new LinkedBlockingQueue<>(dlqCapacity);
    writerPool = Executors.newFixedThreadPool(writerThreads);

    for (int i = 0; i < writerThreads; i++) {
      writerPool.submit(this::writerLoop);
    }

    log.info("[DB_WRITER_INIT] threads={} batchSize={} flushMs={} queueCap={}",
        writerThreads, batchSize, flushIntervalMs, queueCapacity);
  }

  public boolean enqueue(QueueMessage msg) {
    try {
      boolean ok = writeQueue.offer(msg, offerTimeoutMs, TimeUnit.MILLISECONDS);
      if (ok) enqueued.incrementAndGet();
      return ok;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void writerLoop() {
    List<QueueMessage> buffer = new ArrayList<>(batchSize);
    try {
      while (!Thread.currentThread().isInterrupted()) {
        QueueMessage msg = writeQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
        if (msg != null) {
          buffer.add(msg);
          if (buffer.size() >= batchSize) {
            flush(buffer);
          }
        } else if (!buffer.isEmpty()) {
          flush(buffer);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (!buffer.isEmpty()) {
        flush(buffer);
      }
    }
  }

  private void flush(List<QueueMessage> buffer) {
    if (buffer.isEmpty()) return;
    List<QueueMessage> batch = new ArrayList<>(buffer);
    buffer.clear();

    if (!circuitBreaker.allowRequest()) {
      moveToDlq(batch, "circuit_open");
      return;
    }

    long backoff = baseBackoffMs;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        repository.insertBatch(batch);
        persisted.addAndGet(batch.size());
        circuitBreaker.onSuccess();
        evictRoomCache(batch);
        return;
      } catch (Exception e) {
        circuitBreaker.onFailure();
        if (attempt == maxRetries) {
          failed.addAndGet(batch.size());
          moveToDlq(batch, "max_retries");
          return;
        }
        try {
          Thread.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          moveToDlq(batch, "interrupted");
          return;
        }
        backoff = Math.min(backoff * 2, 5_000);
      }
    }
  }

  private void evictRoomCache(List<QueueMessage> batch) {
    Set<String> roomIds = batch.stream()
        .map(QueueMessage::getRoomId)
        .collect(Collectors.toSet());
    for (String roomId : roomIds) {
      Set<String> keys = redisTemplate.keys("room:" + roomId + ":*");
      if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
        log.debug("[CACHE_EVICT] roomId={} keys={}", roomId, keys.size());
      }
    }
  }

  private void moveToDlq(List<QueueMessage> batch, String reason) {
    for (QueueMessage msg : batch) {
      if (!deadLetterQueue.offer(msg)) {
        log.error("[DLQ_OVERFLOW] reason={} msgId={}", reason, msg.getMessageId());
      } else {
        dlqCount.incrementAndGet();
      }
    }
    log.error("[DB_WRITE_FAIL] moved={} reason={}", batch.size(), reason);
  }

  public long getEnqueued() { return enqueued.get(); }
  public long getPersisted() { return persisted.get(); }
  public long getFailed() { return failed.get(); }
  public int getWriteQueueDepth() { return writeQueue.size(); }
  public int getDlqDepth() { return deadLetterQueue.size(); }
  public CircuitBreaker.State getCircuitState() { return circuitBreaker.getState(); }

  @PreDestroy
  public void shutdown() throws Exception {
    if (writerPool != null) {
      writerPool.shutdown();
      writerPool.awaitTermination(10, TimeUnit.SECONDS);
    }
  }
}
