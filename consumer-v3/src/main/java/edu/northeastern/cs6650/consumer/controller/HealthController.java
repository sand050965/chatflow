package edu.northeastern.cs6650.consumer.controller;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import edu.northeastern.cs6650.consumer.service.MessagePersistService;
import edu.northeastern.cs6650.consumer.service.RabbitMQConsumerService;
import edu.northeastern.cs6650.consumer.service.RoomManager;
import edu.northeastern.cs6650.consumer.service.RedisBroadcaster;
import edu.northeastern.cs6650.consumer.service.StatsAggregatorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final RabbitMQConsumerService consumer;
  private final RedisBroadcaster broadcaster;
  private final RoomManager roomManager;
  private final MessagePersistService persistService;
  private final StatsAggregatorService statsAggregator;

  private final AtomicLong lastProcessed = new AtomicLong(0);
  private final AtomicLong lastFailures  = new AtomicLong(0);
  private final AtomicLong lastBroadcast = new AtomicLong(0);

  private volatile double processedPerSec = 0;
  private volatile double failuresPerSec  = 0;
  private volatile double producerRate    = 0;
  private volatile long   totalQueueDepth = 0;
  private volatile long   peakQueueDepth  = 0;

  private final AtomicLong depthSampleSum   = new AtomicLong(0);
  private final AtomicLong depthSampleCount = new AtomicLong(0);

  private final AtomicLong consumerRateSum   = new AtomicLong(0);
  private final AtomicLong consumerRateCount = new AtomicLong(0);
  private final AtomicLong producerRateSum   = new AtomicLong(0);
  private final AtomicLong producerRateCount = new AtomicLong(0);

  private volatile long consumerRateMax = 0;
  private volatile long consumerRateMin = Long.MAX_VALUE;
  private volatile long producerRateMax = 0;
  private volatile long producerRateMin = Long.MAX_VALUE;

  private final AtomicLong lagSampleSum   = new AtomicLong(0);
  private final AtomicLong lagSampleCount = new AtomicLong(0);
  private volatile long lagMax = 0;
  private volatile long lagMin = Long.MAX_VALUE;

  // ignore first 10 seconds for peak queue depth tracking
  private final AtomicLong sampleTick    = new AtomicLong(0);
  private static final long WARMUP_TICKS = 10;

  // queue depth timeline (sampled every 10s)
  private final CopyOnWriteArrayList<long[]> queueTimeline = new CopyOnWriteArrayList<>();
  private static final long TIMELINE_INTERVAL_TICKS = 10;

  private final ScheduledExecutorService sampler =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "metrics-sampler"));

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  public HealthController(RabbitMQConsumerService consumer,
                          RedisBroadcaster broadcaster,
                          RoomManager roomManager,
                          MessagePersistService persistService,
                          StatsAggregatorService statsAggregator) {
    this.consumer    = consumer;
    this.broadcaster = broadcaster;
    this.roomManager = roomManager;
    this.persistService = persistService;
    this.statsAggregator = statsAggregator;
  }

  @PostConstruct
  public void startSampling() {
    sampler.scheduleAtFixedRate(() -> {
      long nowProcessed = consumer.getProcessed();
      long nowFailures  = consumer.getFailures();
      long nowBroadcast = broadcaster.broadcastSuccess.get();

      processedPerSec = nowProcessed - lastProcessed.getAndSet(nowProcessed);
      failuresPerSec  = nowFailures  - lastFailures.getAndSet(nowFailures);
      producerRate    = nowBroadcast - lastBroadcast.getAndSet(nowBroadcast);

      if (processedPerSec > 0) {
        consumerRateSum.addAndGet((long) processedPerSec);
        consumerRateCount.incrementAndGet();
        if ((long) processedPerSec > consumerRateMax) consumerRateMax = (long) processedPerSec;
        if ((long) processedPerSec < consumerRateMin) consumerRateMin = (long) processedPerSec;
      }
      if (producerRate > 0) {
        producerRateSum.addAndGet((long) producerRate);
        producerRateCount.incrementAndGet();
        if ((long) producerRate > producerRateMax) producerRateMax = (long) producerRate;
        if ((long) producerRate < producerRateMin) producerRateMin = (long) producerRate;
      }

      try (Channel ch = consumer.getConnection().createChannel()) {
        long depth = 0;
        for (int i = 1; i <= 20; i++) {
          AMQP.Queue.DeclareOk ok = ch.queueDeclarePassive("room." + i);
          depth += ok.getMessageCount();
        }
        totalQueueDepth = depth;
        depthSampleSum.addAndGet(depth);
        depthSampleCount.incrementAndGet();

        // skip warmup ticks for peak tracking
        long tick = sampleTick.incrementAndGet();
        if (tick > WARMUP_TICKS && depth > peakQueueDepth) peakQueueDepth = depth;

        // record timeline every 10 ticks
        if (tick % TIMELINE_INTERVAL_TICKS == 0) {
          queueTimeline.add(new long[]{Instant.now().getEpochSecond(), depth, (long) processedPerSec});
        }

        // consumer lag sample
        if (processedPerSec > 0) {
          long lag = (long) (depth / processedPerSec * 1000);
          lagSampleSum.addAndGet(lag);
          lagSampleCount.incrementAndGet();
          if (lag > lagMax) lagMax = lag;
          if (lag < lagMin) lagMin = lag;
        }
      } catch (Exception ignored) {}

      if (processedPerSec > 0 || totalQueueDepth > 0) {
        log.info(
            "[CONSUMER_STATS] processed={} rate={}/s failures={} queueDepth={} activeUsers={} activeRooms={}",
            consumer.getProcessed(), (long) processedPerSec,
            consumer.getFailures(), totalQueueDepth,
            roomManager.getActiveUserCount(), roomManager.getActiveRoomCount());
      }

    }, 1, 1, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void printFinalSummary() {
    long cCount = consumerRateCount.get();
    System.out.println("\n============================================================");
    System.out.println("              CONSUMER FINAL SUMMARY");
    System.out.println("============================================================");
    System.out.printf("  Messages Processed : %,d%n", consumer.getProcessed());
    System.out.printf("  Duplicates Skipped : %,d%n", consumer.getDuplicates());
    System.out.printf("  Failures           : %,d%n", consumer.getFailures());
    System.out.printf("  Peak Throughput    : %,d msg/s%n", consumerRateMax);
    System.out.printf("  Avg Throughput     : %,d msg/s%n", cCount > 0 ? consumerRateSum.get() / cCount : 0);
    System.out.printf("  Peak Queue Depth   : %,d msgs%n", peakQueueDepth);
    System.out.printf("  DB Persisted       : %,d%n", persistService.getPersisted());
    System.out.printf("  DB Failed          : %,d%n", persistService.getFailed());
    System.out.printf("  DB DLQ Depth       : %,d%n", persistService.getDlqDepth());
    System.out.printf("  Circuit State      : %s%n", persistService.getCircuitState());
    System.out.println("============================================================\n");
  }

  @GetMapping("/metrics/summary")
  public ResponseEntity<Map<String, Object>> summary() {
    long cCount = consumerRateCount.get();
    long sustainedThroughput = cCount > 0 ? consumerRateSum.get() / cCount : 0;

    List<Map<String, Object>> timeline = new ArrayList<>();
    for (long[] sample : queueTimeline) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("time", Instant.ofEpochSecond(sample[0]).toString());
      point.put("queueDepth", sample[1]);
      point.put("throughput_msg_s", sample[2]);
      timeline.add(point);
    }

    Map<String, Object> m = new LinkedHashMap<>();
    m.put("maximumSustainedThroughput_msg_s", sustainedThroughput);
    m.put("peakThroughput_msg_s", consumerRateMax);
    m.put("peakQueueDepth_msgs", peakQueueDepth);
    m.put("avgQueueDepth_msgs", depthSampleCount.get() > 0
        ? depthSampleSum.get() / depthSampleCount.get() : 0);
    m.put("totalProcessed", consumer.getProcessed());
    m.put("totalFailures", consumer.getFailures());
    m.put("dbPersisted", persistService.getPersisted());
    m.put("dbFailed", persistService.getFailed());
    m.put("circuitState", persistService.getCircuitState().name());
    m.put("queueDepthTimeline", timeline);
    return ResponseEntity.ok(m);
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("ok");
  }

  @GetMapping("/metrics")
  public ResponseEntity<Map<String, Object>> metrics() {
    long cCount   = consumerRateCount.get();
    long pCount   = producerRateCount.get();
    long dCount   = depthSampleCount.get();
    long lagCount = lagSampleCount.get();

    Map<String, Object> m = new LinkedHashMap<>();

    // ── Throughput ───────────────────────────────────────────
    m.put("currentConsumerRate (msg/sec)", (long) processedPerSec);
    m.put("currentProducerRate (msg/sec)", (long) producerRate);
    m.put("avgConsumerRate (msg/sec)",     cCount > 0 ? (double) consumerRateSum.get() / cCount : 0);
    m.put("avgProducerRate (msg/sec)",     pCount > 0 ? (double) producerRateSum.get() / pCount : 0);
    m.put("maxConsumerRate (msg/sec)",     cCount > 0 ? consumerRateMax : 0);
    m.put("minConsumerRate (msg/sec)",     cCount > 0 ? consumerRateMin : 0);
    m.put("maxProducerRate (msg/sec)",     pCount > 0 ? producerRateMax : 0);
    m.put("minProducerRate (msg/sec)",     pCount > 0 ? producerRateMin : 0);
    m.put("failuresPerSec (msg/sec)",      (long) failuresPerSec);

    // ── Totals ───────────────────────────────────────────────
    m.put("totalProcessed (msgs)",         consumer.getProcessed());
    m.put("totalDuplicates (msgs)",        consumer.getDuplicates());
    m.put("totalFailures (msgs)",          consumer.getFailures());
    m.put("broadcastSuccess (msgs)",       broadcaster.broadcastSuccess.get());
    m.put("broadcastFailure (msgs)",       broadcaster.broadcastFailure.get());

    // ── Queue ────────────────────────────────────────────────
    m.put("currentQueueDepth (msgs)",      totalQueueDepth);
    m.put("peakQueueDepth (msgs)",         peakQueueDepth);
    m.put("avgQueueDepth (msgs)",          dCount > 0 ? (double) depthSampleSum.get() / dCount : 0);
    m.put("currentConsumerLag (ms)",       processedPerSec > 0
        ? (long) (totalQueueDepth / processedPerSec * 1000) : -1);
    m.put("avgConsumerLag (ms)",           lagCount > 0 ? (double) lagSampleSum.get() / lagCount : 0);
    m.put("maxConsumerLag (ms)",           lagCount > 0 ? lagMax : 0);
    m.put("minConsumerLag (ms)",           lagCount > 0 ? lagMin : 0);

    // ── Session ──────────────────────────────────────────────
    m.put("activeUsers (count)",           roomManager.getActiveUserCount());
    m.put("activeRooms (count)",           roomManager.getActiveRoomCount());
    m.put("activeThreads (count)",         consumer.getThreadCount());
    m.put("roomStats (users/room)",        roomManager.getRoomStats());

    // ── Persistence ──────────────────────────────────────────
    m.put("dbEnqueued (msgs)",             persistService.getEnqueued());
    m.put("dbPersisted (msgs)",            persistService.getPersisted());
    m.put("dbFailed (msgs)",               persistService.getFailed());
    m.put("dbWriteQueueDepth (msgs)",      persistService.getWriteQueueDepth());
    m.put("dbDLQDepth (msgs)",             persistService.getDlqDepth());
    m.put("dbCircuitState",                persistService.getCircuitState().name());

    // ── Aggregator ───────────────────────────────────────────
    m.put("statsQueueDepth (msgs)",        statsAggregator.getQueueDepth());
    m.put("statsDropped (msgs)",           statsAggregator.getDropped());

    return ResponseEntity.ok(m);
  }
}
