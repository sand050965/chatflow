package edu.northeastern.cs6650.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import edu.northeastern.cs6650.server.config.RabbitMQConfig;
import edu.northeastern.cs6650.server.dto.QueueMessage;
import edu.northeastern.cs6650.server.pool.ChannelPool;
import edu.northeastern.cs6650.server.pool.ConnectionPool;
import edu.northeastern.cs6650.server.util.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQProducerService implements MessageProducer {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQProducerService.class);

  private final ConnectionPool connectionPool;
  private final ObjectMapper mapper;
  private final CircuitBreaker circuitBreaker =
      new CircuitBreaker("RabbitMQ", 5, 30_000, 3);

  private final Counter publishSuccessCounter;
  private final Counter publishFailureCounter;
  private final Timer   publishTimer;

  public final AtomicLong publishSuccess = new AtomicLong();
  public final AtomicLong publishFailure = new AtomicLong();

  public RabbitMQProducerService(ConnectionPool connectionPool, MeterRegistry meterRegistry) {
    this.connectionPool = connectionPool;
    this.mapper         = new ObjectMapper().registerModule(new JavaTimeModule());

    this.publishSuccessCounter = meterRegistry.counter("mq.publish.success");
    this.publishFailureCounter = meterRegistry.counter("mq.publish.failure");
    this.publishTimer          = meterRegistry.timer("mq.publish.latency");
  }

  @Override
  public boolean publish(QueueMessage msg) {
    return publishTimer.record(() -> {
      try {
        return circuitBreaker.call(() -> doPublish(msg));
      } catch (Exception e) {
        publishFailure.incrementAndGet();
        publishFailureCounter.increment();
        log.error("[PUBLISH_FAIL] room={} error={}", msg.getRoomId(), e.getMessage());
        return false;
      }
    });
  }

  private boolean doPublish(QueueMessage msg) {
    String routingKey = "room." + msg.getRoomId();
    ChannelPool cp    = null;
    Channel channel   = null;

    try {
      cp      = connectionPool.borrowChannelPool();
      channel = cp.borrowChannel();

      if (channel == null) {
        log.error("[PUBLISH_FAIL] No channel available for room {}", msg.getRoomId());
        publishFailure.incrementAndGet();
        publishFailureCounter.increment();
        return false;
      }

      byte[] body = mapper.writeValueAsBytes(msg);
      channel.basicPublish(
          RabbitMQConfig.EXCHANGE,
          routingKey,
          MessageProperties.PERSISTENT_TEXT_PLAIN,
          body
      );

      publishSuccess.incrementAndGet();
      publishSuccessCounter.increment();
      return true;

    } catch (Exception e) {
      publishFailure.incrementAndGet();
      publishFailureCounter.increment();
      log.error("[PUBLISH_FAIL] room={} error={}", msg.getRoomId(), e.getMessage());
      if (channel != null) {
        try { channel.abort(); } catch (Exception ignored) {}
        channel = null;
      }
      return false;

    } finally {
      if (cp != null) {
        cp.returnChannel(channel);
        connectionPool.returnChannelPool(cp);
      }
    }
  }

  public CircuitBreaker.State getCircuitState() { return circuitBreaker.getState(); }

  @Override public long getPublishSuccess() { return publishSuccess.get(); }
  @Override public long getPublishFailure() { return publishFailure.get(); }
}