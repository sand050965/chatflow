package edu.northeastern.cs6650.server.pool;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConnectionPool {
  private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);

  @Value("${spring.rabbitmq.host:localhost}")
  private String host;

  @Value("${spring.rabbitmq.port:5672}")
  private int port;

  @Value("${spring.rabbitmq.username:guest}")
  private String username;

  @Value("${spring.rabbitmq.password:guest}")
  private String password;

  @Value("${rabbitmq.pool.connections:2}")
  private int poolSize;

  @Value("${rabbitmq.pool.channels-per-connection:25}")
  private int channelsPerConnection;

  private final BlockingQueue<ChannelPool> pool = new ArrayBlockingQueue<>(10);
  private final AtomicInteger borrowCount = new AtomicInteger();

  @PostConstruct
  public void init() throws IOException, TimeoutException {
    ConnectionFactory factory = buildFactory();

    for (int i = 0; i < poolSize; i++) {
      Connection conn = factory.newConnection("server-pool-" + i);
      ChannelPool cp = new ChannelPool(conn, channelsPerConnection);
      cp.init();
      pool.offer(cp);
      log.info("[CONN_POOL] Created connection {} of {}", i + 1, poolSize);
    }

    log.info("[CONN_POOL] Ready — {} connections, {} channels each",
        poolSize, channelsPerConnection);
  }

  /**
   * Borrow a ChannelPool from the connection pool.
   * Round-robins across connections using a blocking queue.
   * Blocks up to 5 s if all ChannelPools are in use.
   */
  public ChannelPool borrowChannelPool() throws InterruptedException {
    ChannelPool cp = pool.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    if (cp == null) {
      throw new RuntimeException("ConnectionPool exhausted — no ChannelPool available");
    }
    borrowCount.incrementAndGet();
    return cp;
  }

  /**
   * Return a ChannelPool back to the connection pool.
   */
  public void returnChannelPool(ChannelPool cp) {
    pool.offer(cp);
  }

  @PreDestroy
  public void shutdown() {
    log.info("[CONN_POOL] Shutting down...");
    for (ChannelPool cp : pool) {
      cp.shutdown();
    }
    log.info("[CONN_POOL] Shut down. Total borrows: {}", borrowCount.get());
  }

  private ConnectionFactory buildFactory() {
    ConnectionFactory f = new ConnectionFactory();
    f.setHost(host);
    f.setPort(port);
    f.setUsername(username);
    f.setPassword(password);
    f.setConnectionTimeout(5_000);
    f.setRequestedHeartbeat(60);
    f.setAutomaticRecoveryEnabled(true);
    f.setNetworkRecoveryInterval(5_000);
    return f;
  }
}
