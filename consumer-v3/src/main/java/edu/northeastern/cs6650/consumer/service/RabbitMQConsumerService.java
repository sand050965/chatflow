package edu.northeastern.cs6650.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQConsumerService {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumerService.class);
  private static final int ROOM_COUNT = 20;

  @Value("${spring.rabbitmq.host:localhost}")  private String  host;
  @Value("${spring.rabbitmq.port:5672}")        private int     port;
  @Value("${spring.rabbitmq.username:guest}")   private String  username;
  @Value("${spring.rabbitmq.password:guest}")   private String  password;
  @Value("${consumer.thread-count:20}")         private int     threadCount;
  @Value("${consumer.prefetch:50}")             private int     prefetch;
  @Value("${consumer.batch-size:10}")           private int     batchSize;
  @Value("${consumer.polling-mode:false}")      private boolean pollingMode;
  @Value("${consumer.poll-interval-ms:10}")     private long    pollIntervalMs;

  private Connection connection;
  private final List<Channel> channels      = Collections.synchronizedList(new ArrayList<>());
  private ExecutorService      workerPool;
  private ExecutorService      broadcastExecutor;
  private ScheduledExecutorService flushScheduler;

  private final Set<String> seenIds = Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(
          new java.util.LinkedHashMap<>(50_000, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> e) {
              return size() > 50_000;
            }
          }
      )
  );

  private final RedisBroadcaster   broadcaster;
  private final RoomManager        roomManager;
  private final MessagePersistService persistService;
  private final StatsAggregatorService statsAggregator;
  private final MeterRegistry     meterRegistry;
  private final ObjectMapper      mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Counter processedCounter;
  private Counter duplicatesCounter;
  private Counter failuresCounter;
  private Counter gateRejectCounter;
  private Counter retryCounter;
  private Timer   processTimer;

  public final AtomicLong processed  = new AtomicLong();
  public final AtomicLong duplicates = new AtomicLong();
  public final AtomicLong failures   = new AtomicLong();

  public RabbitMQConsumerService(RedisBroadcaster broadcaster,
                                 RoomManager roomManager,
                                 MessagePersistService persistService,
                                 StatsAggregatorService statsAggregator,
                                 MeterRegistry meterRegistry) {
    this.broadcaster   = broadcaster;
    this.roomManager   = roomManager;
    this.persistService = persistService;
    this.statsAggregator = statsAggregator;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void init() throws Exception {
    processedCounter  = meterRegistry.counter("consumer.processed");
    duplicatesCounter = meterRegistry.counter("consumer.duplicates");
    failuresCounter   = meterRegistry.counter("consumer.failures");
    gateRejectCounter = meterRegistry.counter("consumer.gate.rejected");
    retryCounter      = meterRegistry.counter("consumer.retries");
    processTimer      = meterRegistry.timer("consumer.process.latency");

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setAutomaticRecoveryEnabled(true);
    factory.setNetworkRecoveryInterval(5_000);

    connection        = factory.newConnection("consumer");
    workerPool        = Executors.newFixedThreadPool(threadCount);
    broadcastExecutor = Executors.newFixedThreadPool(threadCount * 2);
    flushScheduler    = Executors.newScheduledThreadPool(threadCount);

    List<List<Integer>> assignments = assignRooms(ROOM_COUNT, threadCount);
    for (List<Integer> roomIds : assignments) {
      if (roomIds.isEmpty()) continue;
      workerPool.submit(() -> startConsumer(roomIds));
    }

    log.info("[CONSUMER_INIT] threads={} rooms={} prefetch={} batchSize={} mode={}",
        threadCount, ROOM_COUNT, prefetch, batchSize, pollingMode ? "poll" : "push");
  }

  private void startConsumer(List<Integer> roomIds) {
    if (pollingMode) startPollingConsumer(roomIds);
    else             startPushConsumer(roomIds);
  }

  private void startPushConsumer(List<Integer> roomIds) {
    try {
      Channel channel = connection.createChannel();
      channel.basicQos(prefetch);
      channels.add(channel);
      channel.exchangeDeclare("chat.exchange", "topic", true);

      for (int roomId : roomIds) {
        String queue = "room." + roomId;
        channel.queueDeclare(queue, true, false, false, Map.of(
            "x-message-ttl", 60_000,
            "x-max-length",  100_000,
            "x-overflow",    "drop-head"
        ));
        channel.queueBind(queue, "chat.exchange", queue);
      }

      List<QueueMessage> batchBuffer = new ArrayList<>();
      List<Long>         batchTags   = new ArrayList<>();

      flushScheduler.scheduleAtFixedRate(() -> {
        synchronized (batchBuffer) {
          if (!batchBuffer.isEmpty()) {
            flushBatch(channel, batchBuffer, batchTags);
          }
        }
      }, 100, 100, TimeUnit.MILLISECONDS);

      DeliverCallback deliver = (consumerTag, delivery) -> {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        String msgId = null;
        QueueMessage msg = null;
        try {
          msg   = mapper.readValue(delivery.getBody(), QueueMessage.class);
          msgId = msg.getMessageId();

          if (!seenIds.add(msgId)) {
            duplicates.incrementAndGet();
            duplicatesCounter.increment();
            log.debug("[DUPLICATE] msgId={}", msgId);
            channel.basicAck(deliveryTag, false);
            return;
          }

          String type = msg.getMessageType();
          if ("JOIN".equals(type))        roomManager.handleJoin(msg);
          else if ("LEAVE".equals(type))  roomManager.handleLeave(msg);
          else if ("TEXT".equals(type))   roomManager.recordMessage(msg.getUserId());

          if (!persistService.enqueue(msg)) {
            throw new IllegalStateException("persist_queue_full");
          }
          statsAggregator.enqueue(msg);

          if (!roomManager.hasActiveUsers(msg.getRoomId())) {
            log.debug("[SKIP_BROADCAST] room={} no active users", msg.getRoomId());
            channel.basicAck(deliveryTag, false);
            processed.incrementAndGet();
            processedCounter.increment();
            return;
          }

          synchronized (batchBuffer) {
            batchBuffer.add(msg);
            batchTags.add(deliveryTag);
            if (batchBuffer.size() >= batchSize) {
              flushBatch(channel, batchBuffer, batchTags);
            }
          }

        } catch (Exception e) {
          failures.incrementAndGet();
          failuresCounter.increment();
          log.error("[CONSUME_FAIL] msgId={} error={}", msgId, e.getMessage());
          try {
            Map<String, Object> headers = delivery.getProperties().getHeaders();
            int retryCount = headers != null && headers.containsKey("x-retry-count")
                ? (int) headers.get("x-retry-count") : 0;
            if (retryCount < 3) {
              var props = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                  .headers(Map.of("x-retry-count", retryCount + 1))
                  .deliveryMode(2).build();
              channel.basicPublish("chat.exchange",
                  msg != null ? "room." + msg.getRoomId() : "room.unknown",
                  props, delivery.getBody());
              retryCounter.increment();
              log.warn("[CONSUME_RETRY] msgId={} attempt={}/3", msgId, retryCount + 1);
            } else {
              log.error("[CONSUME_GIVE_UP] msgId={}", msgId);
            }
            channel.basicAck(deliveryTag, false);
          } catch (Exception ignored) {}
        }
      };

      for (int roomId : roomIds) {
        String queue = "room." + roomId;
        channel.basicConsume(queue, false, deliver,
            t -> log.warn("[CONSUMER_CANCELLED] queue={}", queue));
        log.info("[CONSUMER_INIT] thread={} listening on {}",
            Thread.currentThread().getName(), queue);
      }

    } catch (Exception e) {
      log.error("[CONSUMER_ERROR] thread={} type={} error={}",
          Thread.currentThread().getName(), e.getClass().getSimpleName(), e.getMessage(), e);
    }
  }

  private void startPollingConsumer(List<Integer> roomIds) {
    try {
      Channel channel = connection.createChannel();
      channel.basicQos(prefetch);
      channels.add(channel);
      channel.exchangeDeclare("chat.exchange", "topic", true);

      for (int roomId : roomIds) {
        String queue = "room." + roomId;
        channel.queueDeclare(queue, true, false, false, Map.of(
            "x-message-ttl", 60_000,
            "x-max-length",  100_000,
            "x-overflow",    "drop-head"
        ));
        channel.queueBind(queue, "chat.exchange", queue);
      }

      List<QueueMessage> batchBuffer = new ArrayList<>();
      List<Long>         batchTags   = new ArrayList<>();

      log.info("[CONSUMER_INIT] thread={} polling {} rooms every {}ms",
          Thread.currentThread().getName(), roomIds.size(), pollIntervalMs);

      while (!Thread.currentThread().isInterrupted()) {
        boolean gotAny = false;
        for (int roomId : roomIds) {
          String queue = "room." + roomId;
          com.rabbitmq.client.GetResponse resp = channel.basicGet(queue, false);
          if (resp == null) continue;

          gotAny = true;
          long   deliveryTag = resp.getEnvelope().getDeliveryTag();
          byte[] body        = resp.getBody();
          Map<String, Object> headers = resp.getProps().getHeaders();
          String msgId = null;

          try {
            QueueMessage msg = mapper.readValue(body, QueueMessage.class);
            msgId = msg.getMessageId();

            if (!seenIds.add(msgId)) {
              duplicates.incrementAndGet();
              duplicatesCounter.increment();
              channel.basicAck(deliveryTag, false);
              continue;
            }

            String type = msg.getMessageType();
            if ("JOIN".equals(type))        roomManager.handleJoin(msg);
            else if ("LEAVE".equals(type))  roomManager.handleLeave(msg);
            else if ("TEXT".equals(type))   roomManager.recordMessage(msg.getUserId());

            if (!persistService.enqueue(msg)) {
              throw new IllegalStateException("persist_queue_full");
            }
            statsAggregator.enqueue(msg);

            if (!roomManager.hasActiveUsers(msg.getRoomId())) {
              channel.basicAck(deliveryTag, false);
              processed.incrementAndGet();
              processedCounter.increment();
              continue;
            }

            batchBuffer.add(msg);
            batchTags.add(deliveryTag);

            if (batchBuffer.size() >= batchSize) {
              flushBatch(channel, batchBuffer, batchTags);
            }

          } catch (Exception e) {
            failures.incrementAndGet();
            failuresCounter.increment();
            log.error("[POLL_FAIL] msgId={} error={}", msgId, e.getMessage());
            try {
              int retryCount = headers != null && headers.containsKey("x-retry-count")
                  ? (int) headers.get("x-retry-count") : 0;
              if (retryCount < 3) {
                var props = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                    .headers(Map.of("x-retry-count", retryCount + 1))
                    .deliveryMode(2).build();
                channel.basicPublish("chat.exchange", queue, props, body);
                retryCounter.increment();
              } else {
                log.error("[POLL_GIVE_UP] msgId={}", msgId);
              }
              channel.basicAck(deliveryTag, false);
            } catch (Exception ignored) {}
          }
        }
        if (!gotAny) Thread.sleep(pollIntervalMs);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("[POLL_ERROR] thread={} error={}",
          Thread.currentThread().getName(), e.getMessage(), e);
    }
  }

  private void flushBatch(Channel channel, List<QueueMessage> buffer, List<Long> tags) {
    if (buffer.isEmpty()) return;
    List<QueueMessage> copy    = new ArrayList<>(buffer);
    long               lastTag = tags.get(tags.size() - 1);
    int                size    = copy.size();
    try {
      channel.basicAck(lastTag, true);
      processed.addAndGet(size);
      processedCounter.increment(size);
      log.debug("[BATCH_ACK] size={} lastTag={}", size, lastTag);

      broadcastExecutor.submit(() -> {
        try {
          broadcaster.broadcastBatch(copy);
        } catch (Exception e) {
          log.error("[BROADCAST_ASYNC_FAIL] error={}", e.getMessage());
        }
      });
    } catch (Exception e) {
      failures.addAndGet(size);
      failuresCounter.increment();
      log.error("[BATCH_FLUSH_FAIL] error={}", e.getMessage());
    } finally {
      buffer.clear();
      tags.clear();
    }
  }

  private List<List<Integer>> assignRooms(int roomCount, int threads) {
    List<List<Integer>> result = new ArrayList<>();
    for (int i = 0; i < threads; i++) result.add(new ArrayList<>());
    for (int r = 1; r <= roomCount; r++) result.get((r - 1) % threads).add(r);
    return result;
  }

  public long       getProcessed()       { return processed.get(); }
  public long       getDuplicates()      { return duplicates.get(); }
  public long       getFailures()        { return failures.get(); }
  public int        getActiveUserCount() { return roomManager.getActiveUserCount(); }
  public int        getThreadCount()     { return threadCount; }
  public int        getBatchSize()       { return batchSize; }
  public Connection getConnection()      { return connection; }

  @PreDestroy
  public void shutdown() throws Exception {
    log.info("[CONSUMER_SHUTDOWN] processed={} dup={} fail={}",
        processed.get(), duplicates.get(), failures.get());
    flushScheduler.shutdown();
    workerPool.shutdown();
    broadcastExecutor.shutdown();
    flushScheduler.awaitTermination(5, TimeUnit.SECONDS);
    workerPool.awaitTermination(10, TimeUnit.SECONDS);
    broadcastExecutor.awaitTermination(10, TimeUnit.SECONDS);
    for (Channel ch : channels) {
      try { if (ch.isOpen()) ch.close(); } catch (Exception ignored) {}
    }
    if (connection.isOpen()) connection.close();
  }
}
