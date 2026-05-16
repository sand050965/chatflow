package edu.northeastern.cs6650.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.northeastern.cs6650.server.dto.QueueMessage;
import edu.northeastern.cs6650.server.handler.ChatWebSocketHandler;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class RedisSubscriber {

  private static final Logger log = LoggerFactory.getLogger(RedisSubscriber.class);
  private static final String CHANNEL_PREFIX = "room:";

  private final ChatWebSocketHandler handler;
  private final RedisMessageListenerContainer container;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ExecutorService broadcastPool = Executors.newFixedThreadPool(20);
  private final ConcurrentHashMap<String, MessageListener> listeners = new ConcurrentHashMap<>();

  // @Lazy breaks the circular dependency: ChatWebSocketHandler -> RedisSubscriber -> ChatWebSocketHandler
  public RedisSubscriber(@Lazy ChatWebSocketHandler handler, RedisMessageListenerContainer container) {
    this.handler = handler;
    this.container = container;
  }

  public void subscribe(String roomId) {
    listeners.computeIfAbsent(roomId, rid -> {
      MessageListener listener = (message, pattern) -> onMessage(rid, message);
      container.addMessageListener(listener, new ChannelTopic(CHANNEL_PREFIX + rid));
      log.info("[REDIS_SUBSCRIBE] channel={}", CHANNEL_PREFIX + rid);
      return listener;
    });
  }

  public void unsubscribeIfEmpty(String roomId) {
    if (handler.getSessionsForRoom(roomId).isEmpty()) {
      MessageListener listener = listeners.remove(roomId);
      if (listener != null) {
        container.removeMessageListener(listener, new ChannelTopic(CHANNEL_PREFIX + roomId));
        log.info("[REDIS_UNSUBSCRIBE] channel={}", CHANNEL_PREFIX + roomId);
      }
    }
  }

  private void onMessage(String roomId, Message message) {
    try {
      List<QueueMessage> msgs = mapper.readValue(message.getBody(),
          new TypeReference<List<QueueMessage>>() {});

      CopyOnWriteArrayList<WebSocketSession> sessions = handler.getSessionsForRoom(roomId);
      if (sessions.isEmpty()) return;

      for (QueueMessage msg : msgs) {
        String json = mapper.writeValueAsString(msg);
        TextMessage frame = new TextMessage(json);
        for (WebSocketSession session : sessions) {
          broadcastPool.submit(() -> {
            try {
              if (session.isOpen()) {
                synchronized (session) {
                  session.sendMessage(frame);
                }
              }
            } catch (Exception e) {
              log.warn("[WS_SEND_FAIL] session={} error={}", session.getId(), e.getMessage());
            }
          });
        }
      }

      log.debug("[REDIS_RECV] room={} msgs={} sessions={}", roomId, msgs.size(), sessions.size());
    } catch (Exception e) {
      log.error("[REDIS_RECV_FAIL] room={} error={}", roomId, e.getMessage());
    }
  }
}
