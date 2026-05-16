package edu.northeastern.cs6650.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.northeastern.cs6650.server.dto.ChatMessage;
import edu.northeastern.cs6650.server.dto.ChatResponse;
import edu.northeastern.cs6650.server.dto.ErrorResponse;
import edu.northeastern.cs6650.server.dto.QueueMessage;
import edu.northeastern.cs6650.server.service.MessageProducer;
import edu.northeastern.cs6650.server.service.RabbitMQProducerService;
import edu.northeastern.cs6650.server.service.RedisSubscriber;
import edu.northeastern.cs6650.server.service.SessionStateService;
import edu.northeastern.cs6650.server.util.CircuitBreaker;
import edu.northeastern.cs6650.server.util.RateLimiter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private static final int MIN_USER_ID = 1;
  private static final int MAX_USER_ID = 100_000;
  private static final int MIN_USERNAME_LEN = 3;
  private static final int MAX_USERNAME_LEN = 20;
  private static final int MAX_MESSAGE_LEN = 500;
  private static final String USERNAME_REGEX = "^[a-zA-Z0-9]+$";
  private static final String MSG_TYPE_REGEX = "^(TEXT|JOIN|LEAVE)$";

  private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>>
      roomSessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> sessionRoom = new ConcurrentHashMap<>();

  public final AtomicLong totalReceived = new AtomicLong();
  public final AtomicLong totalPublished = new AtomicLong();
  public final AtomicLong totalRejected = new AtomicLong();
  public final AtomicLong totalConnected = new AtomicLong();

  private final SessionStateService stateService;
  private final MessageProducer producer;
  private final RedisSubscriber redisSubscriber;
  private final ObjectMapper mapper;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .build();

  @Value("${consumer.url:http://localhost:8081}")
  private String consumerUrl;

  @Value("${rate.limit.requests-per-second:100}")
  private int rateLimitRequestsPerSecond;

  private RateLimiter rateLimiter;

  @Value("${internal.secret:secret}")
  private String internalSecret;

  @Value("${server.url:http://localhost:8080}")
  private String serverUrl;

  @Value("${server.id:${random.uuid}}")
  private String serverId;

  public ChatWebSocketHandler(SessionStateService stateService, MessageProducer producer,
      RedisSubscriber redisSubscriber) {
    this.stateService = stateService;
    this.producer = producer;
    this.redisSubscriber = redisSubscriber;
    this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @jakarta.annotation.PostConstruct
  public void init() {
    rateLimiter = new RateLimiter(rateLimitRequestsPerSecond, 1000);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    String roomId = extractRoomId(session);
    roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(session);
    sessionRoom.put(session.getId(), roomId);
    long connectedNow = totalConnected.incrementAndGet();
    log.info("[CONNECT] session={} room={} roomSize={} totalConnected={}",
        session.getId(), roomId, roomSessions.get(roomId).size(), connectedNow);

    redisSubscriber.subscribe(roomId);
    notifyConsumerRegister(session.getId(), roomId);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage raw) {
    totalReceived.incrementAndGet();
    String roomId = sessionRoom.get(session.getId());

    if (!rateLimiter.allowRequest(session.getId())) {
      totalRejected.incrementAndGet();
      reply(session, error("Rate limit exceeded — max " + rateLimitRequestsPerSecond + " msg/s"));
      return;
    }

    if (isQueueUnavailable()) {
      totalRejected.incrementAndGet();
      long cooldownSec = getCircuitCooldownSec();
      reply(session, error(
          "Queue temporarily unavailable. Please retry in " + cooldownSec + "s"));
      log.warn("[CIRCUIT_OPEN] Rejected message for room={}", roomId);
      return;
    }

    ChatMessage msg;
    try {
      msg = mapper.readValue(raw.getPayload(), ChatMessage.class);
    } catch (JsonProcessingException e) {
      totalRejected.incrementAndGet();
      reply(session, error("Malformed JSON: " + e.getOriginalMessage()));
      return;
    }

    String fieldError = validate(msg);
    if (fieldError != null) {
      totalRejected.incrementAndGet();
      reply(session, error(fieldError));
      return;
    }

    String type = msg.getMessageType();
    if ("JOIN".equals(type)) {
      stateService.activate(session.getId());
      log.info("[JOIN] session={} room={} userId={}", session.getId(), roomId, msg.getUserId());
    } else if ("LEAVE".equals(type)) {
      stateService.deactivate(session.getId());
      log.info("[LEAVE] session={} room={} userId={}", session.getId(), roomId, msg.getUserId());
    }

    QueueMessage queueMsg = QueueMessage.from(msg, roomId, serverId, getClientIp(session));
    boolean published = producer.publish(queueMsg);

    if (published) {
      totalPublished.incrementAndGet();
      reply(session, new ChatResponse("SUCCESS", "Message queued",
          Instant.now().toString(), msg));
    } else {
      totalRejected.incrementAndGet();
      if (isQueueUnavailable()) {
        long cooldownSec = getCircuitCooldownSec();
        reply(session, error(
            "Queue temporarily unavailable. Please retry in " + cooldownSec + "s"));
      } else {
        reply(session, error("Queue unavailable — please retry"));
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomId = sessionRoom.get(session.getId());
    cleanup(session);
    notifyConsumerUnregister(session.getId());
    if (roomId != null) redisSubscriber.unsubscribeIfEmpty(roomId);
    log.info("[DISCONNECT] session={} status={}", session.getId(), status);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable ex) {
    String roomId = sessionRoom.get(session.getId());
    cleanup(session);
    notifyConsumerUnregister(session.getId());
    if (roomId != null) redisSubscriber.unsubscribeIfEmpty(roomId);
    log.error("[TRANSPORT_ERROR] session={} error={}", session.getId(), ex.getMessage());
  }

  public CopyOnWriteArrayList<WebSocketSession> getSessionsForRoom(String roomId) {
    return roomSessions.getOrDefault(roomId, new CopyOnWriteArrayList<>());
  }

  public int totalConnected() {
    return sessionRoom.size();
  }

  public Map<String, Long> getStatistics() {
    return Map.of(
        "totalConnected", (long) totalConnected(),
        "totalReceived", totalReceived.get(),
        "totalPublished", totalPublished.get(),
        "totalRejected", totalRejected.get(),
        "mqSuccess", producer.getPublishSuccess(),
        "mqFailure", producer.getPublishFailure()
    );
  }

  private void notifyConsumerUpdateUser(String sessionId, String userId, String username) {
    try {
      String body = String.format(
          "{\"sessionId\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\"}",
          sessionId, userId, username);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(consumerUrl + "/session/update-user"))
          .timeout(Duration.ofSeconds(3))
          .header("Content-Type", "application/json")
          .header("X-Internal-Secret", internalSecret)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

      httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
          .thenAccept(r -> {
            if (r.statusCode() != 200) {
              log.warn("[UPDATE_USER_FAIL] session={} status={}", sessionId, r.statusCode());
            }
          });
    } catch (Exception e) {
      log.warn("[UPDATE_USER_FAIL] session={} error={}", sessionId, e.getMessage());
    }
  }

  private void notifyConsumerRegister(String sessionId, String roomId) {
    try {
      String body = String.format(
          "{\"serverId\":\"%s\",\"sessionId\":\"%s\",\"serverUrl\":\"%s\",\"roomId\":\"%s\"}",
          serverId, sessionId, serverUrl, roomId);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(consumerUrl + "/session/register"))
          .timeout(Duration.ofSeconds(3))
          .header("Content-Type", "application/json")
          .header("X-Internal-Secret", internalSecret)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

      httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
          .thenAccept(r -> {
            if (r.statusCode() != 200) {
              log.warn("[SESSION_REG_FAIL] session={} status={}", sessionId, r.statusCode());
            }
          });
    } catch (Exception e) {
      log.warn("[SESSION_REG_FAIL] session={} error={}", sessionId, e.getMessage());
    }
  }

  private void notifyConsumerUnregister(String sessionId) {
    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(consumerUrl + "/session/unregister/" + sessionId))
          .timeout(Duration.ofSeconds(3))
          .header("X-Internal-Secret", internalSecret)
          .DELETE()
          .build();

      httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
          .thenAccept(r -> {
            if (r.statusCode() != 200) {
              log.warn("[SESSION_UNREG_FAIL] session={} status={}", sessionId, r.statusCode());
            }
          });
    } catch (Exception e) {
      log.warn("[SESSION_UNREG_FAIL] session={} error={}", sessionId, e.getMessage());
    }
  }

  private boolean isQueueUnavailable() {
    if (producer instanceof RabbitMQProducerService r) {
      return r.getCircuitState() == CircuitBreaker.State.OPEN;
    }
    return false;
  }

  private long getCircuitCooldownSec() {
    return 30;
  }

  private void cleanup(WebSocketSession session) {
    String roomId = sessionRoom.remove(session.getId());
    if (roomId != null) {
      CopyOnWriteArrayList<WebSocketSession> list = roomSessions.get(roomId);
      if (list != null) {
        list.remove(session);
        if (list.isEmpty()) {
          roomSessions.remove(roomId);
        }
      }
    }
    stateService.deactivate(session.getId());
    rateLimiter.evict(session.getId());
  }

  public void reply(WebSocketSession session, Object payload) {
    if (!session.isOpen()) {
      return;
    }
    try {
      String json = mapper.writeValueAsString(payload);
      synchronized (session) {
        session.sendMessage(new TextMessage(json));
      }
    } catch (IOException e) {
      log.error("[SEND_ERROR] session={} error={}", session.getId(), e.getMessage());
    }
  }

  private ErrorResponse error(String msg) {
    return new ErrorResponse("ERROR", msg, Instant.now().toString());
  }

  private String extractRoomId(WebSocketSession session) {
    String path = session.getUri().getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private String getClientIp(WebSocketSession session) {
    InetSocketAddress addr = session.getRemoteAddress();
    return addr != null ? addr.getAddress().getHostAddress() : "unknown";
  }

  private String validate(ChatMessage m) {
    if (m.getUserId() == null || m.getUserId().isBlank()) {
      return "userId is required";
    }
    try {
      int uid = Integer.parseInt(m.getUserId());
      if (uid < MIN_USER_ID || uid > MAX_USER_ID) {
        return "userId must be " + MIN_USER_ID + "–" + MAX_USER_ID;
      }
    } catch (NumberFormatException e) {
      return "userId must be numeric";
    }

    if (m.getUsername() == null || !m.getUsername().matches(USERNAME_REGEX)
        || m.getUsername().length() < MIN_USERNAME_LEN
        || m.getUsername().length() > MAX_USERNAME_LEN) {
      return "username must be 3–20 alphanumeric characters";
    }

    if (m.getMessage() == null || m.getMessage().isEmpty()
        || m.getMessage().length() > MAX_MESSAGE_LEN) {
      return "message must be 1–500 characters";
    }

    if (m.getTimestamp() == null || m.getTimestamp().isBlank()) {
      return "timestamp is required";
    }
    try {
      Instant.parse(m.getTimestamp());
    } catch (DateTimeParseException e) {
      return "timestamp must be ISO-8601";
    }

    if (m.getMessageType() == null || !m.getMessageType().matches(MSG_TYPE_REGEX)) {
      return "messageType must be TEXT, JOIN, or LEAVE";
    }

    return null;
  }
}