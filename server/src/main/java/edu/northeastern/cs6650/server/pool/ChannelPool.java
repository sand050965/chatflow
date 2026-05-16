package edu.northeastern.cs6650.server.pool;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelPool {
  private static final Logger log = LoggerFactory.getLogger(ChannelPool.class);

  private final BlockingQueue<Channel> pool;
  private final Connection connection;
  private final int size;
  private final AtomicInteger borrowCount = new AtomicInteger();

  public ChannelPool(Connection connection, int size) {
    this.connection = connection;
    this.size = size;
    this.pool = new ArrayBlockingQueue<>(size);
  }

  public void init() throws IOException {
    for (int i = 0; i < size; i++) {
      Channel ch = connection.createChannel();
      ch.confirmSelect();

      ch.addConfirmListener(
          (tag, multiple) -> log.debug("[CONFIRM_ACK] tag={} multiple={}", tag, multiple),
          (tag, multiple) -> log.warn("[CONFIRM_NACK] tag={} multiple={}", tag, multiple)
      );

      pool.offer(ch);
    }
    log.debug("[CHANNEL_POOL] Initialized {} channels for connection {}",
        size, connection.getClientProvidedName());
  }

  public Channel borrowChannel() throws InterruptedException {
    Channel ch = pool.poll(3, TimeUnit.SECONDS);
    if (ch == null) {
      log.warn("[CHANNEL_POOL] Pool exhausted — no channel available after 3s");
      return null;
    }
    borrowCount.incrementAndGet();
    return ch;
  }

  public void returnChannel(Channel channel) {
    if (channel != null && channel.isOpen()) {
      pool.offer(channel);
    } else {
      try {
        Channel fresh = connection.createChannel();
        fresh.confirmSelect();
        fresh.addConfirmListener(
            (tag, multiple) -> log.debug("[CONFIRM_ACK] tag={} multiple={}", tag, multiple),
            (tag, multiple) -> log.warn("[CONFIRM_NACK] tag={} multiple={}", tag, multiple)
        );
        pool.offer(fresh);
        log.warn("[CHANNEL_POOL] Replaced broken channel — pool size maintained");
      } catch (IOException e) {
        log.error("[CHANNEL_POOL] Failed to replace broken channel: {}", e.getMessage());
      }
    }
  }

  public void shutdown() {
    for (Channel ch : pool) {
      try {
        if (ch.isOpen()) {
          ch.close();
        }
      } catch (Exception ignored) {
      }
    }
    try {
      if (connection.isOpen()) {
        connection.close();
      }
    } catch (Exception ignored) {
    }
    log.info("[CHANNEL_POOL] Shut down. Total borrows: {}", borrowCount.get());
  }

  public int available() {
    return pool.size();
  }
}
