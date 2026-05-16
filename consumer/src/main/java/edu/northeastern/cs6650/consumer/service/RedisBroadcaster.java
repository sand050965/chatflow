package edu.northeastern.cs6650.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(RedisBroadcaster.class);
  private static final String CHANNEL_PREFIX = "room:";

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public final AtomicLong broadcastSuccess = new AtomicLong();
  public final AtomicLong broadcastFailure = new AtomicLong();

  public RedisBroadcaster(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void broadcastBatch(List<QueueMessage> msgs) {
    if (msgs.isEmpty()) return;

    String roomId = msgs.get(0).getRoomId();
    String channel = CHANNEL_PREFIX + roomId;

    try {
      String payload = mapper.writeValueAsString(msgs);
      redis.convertAndSend(channel, payload);
      broadcastSuccess.addAndGet(msgs.size());
      log.debug("[REDIS_PUBLISH] channel={} msgs={}", channel, msgs.size());
    } catch (Exception e) {
      broadcastFailure.addAndGet(msgs.size());
      log.error("[REDIS_PUBLISH_FAIL] channel={} error={}", channel, e.getMessage());
    }
  }
}
