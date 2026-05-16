package edu.northeastern.cs6650.consumer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs6650.consumer.dto.IdCount;
import edu.northeastern.cs6650.consumer.dto.MessageRecord;
import edu.northeastern.cs6650.consumer.dto.RoomActivity;
import edu.northeastern.cs6650.consumer.dto.TimeBucketCount;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
  private static final Duration MATERIALIZED_VIEW_WINDOW = Duration.ofHours(24);
  private static final Duration VIEW_RANGE_TOLERANCE = Duration.ofMinutes(1);

  private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

  private static final RowMapper<MessageRecord> MESSAGE_MAPPER = (rs, i) -> new MessageRecord(
      rs.getString("message_id"),
      rs.getString("room_id"),
      rs.getString("user_id"),
      rs.getString("username"),
      rs.getString("message"),
      rs.getString("message_type"),
      rs.getString("server_id"),
      rs.getString("client_ip"),
      toInstant(rs.getTimestamp("created_at"))
  );

  private static final RowMapper<RoomActivity> ROOM_ACTIVITY_MAPPER = (rs, i) ->
      new RoomActivity(rs.getString("room_id"), toInstant(rs.getTimestamp("last_activity")));

  private static final RowMapper<IdCount> ID_COUNT_MAPPER = (rs, i) ->
      new IdCount(rs.getString("id"), rs.getLong("cnt"));

  private static final RowMapper<TimeBucketCount> BUCKET_MAPPER = (rs, i) ->
      new TimeBucketCount(toInstant(rs.getTimestamp("bucket")), rs.getLong("cnt"));

  private final JdbcTemplate jdbcTemplate;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  private final Duration roomMessagesTtl;
  private final Duration userHistoryTtl;
  private final Duration activeUsersTtl;
  private final Duration aggregationTtl;

  public AnalyticsService(
      JdbcTemplate jdbcTemplate,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${analytics.room-messages-ttl-ms:3000}") long roomMessagesTtlMs,
      @Value("${analytics.user-history-ttl-ms:10000}") long userHistoryTtlMs,
      @Value("${analytics.active-users-ttl-ms:5000}") long activeUsersTtlMs,
      @Value("${analytics.aggregation-ttl-ms:5000}") long aggregationTtlMs) {
    this.jdbcTemplate = jdbcTemplate;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.roomMessagesTtl = Duration.ofMillis(roomMessagesTtlMs);
    this.userHistoryTtl = Duration.ofMillis(userHistoryTtlMs);
    this.activeUsersTtl = Duration.ofMillis(activeUsersTtlMs);
    this.aggregationTtl = Duration.ofMillis(aggregationTtlMs);
  }

  public List<MessageRecord> getMessagesForRoom(String roomId, Instant start, Instant end, int limit) {
    String key = "room:" + roomId + ":" + start + ":" + end + ":" + limit;
    return cached(key, roomMessagesTtl, () -> jdbcTemplate.query("""
        SELECT message_id, room_id, user_id, username, message, message_type, server_id, client_ip, created_at
        FROM messages
        WHERE room_id = ? AND created_at BETWEEN ? AND ?
        ORDER BY created_at
        LIMIT ?
        """, MESSAGE_MAPPER, roomId, ts(start), ts(end), limit),
        new TypeReference<>() {});
  }

  public List<MessageRecord> getUserHistory(String userId, Instant start, Instant end, int limit) {
    String key = "user:" + userId + ":" + start + ":" + end + ":" + limit;
    return cached(key, userHistoryTtl, () -> jdbcTemplate.query("""
        SELECT message_id, room_id, user_id, username, message, message_type, server_id, client_ip, created_at
        FROM messages
        WHERE user_id = ? AND created_at BETWEEN ? AND ?
        ORDER BY created_at
        LIMIT ?
        """, MESSAGE_MAPPER, userId, ts(start), ts(end), limit),
        new TypeReference<>() {});
  }

  public long countActiveUsers(Instant start, Instant end) {
    String key = "activeUsers:" + start + ":" + end;
    Long val = cached(key, activeUsersTtl, () -> jdbcTemplate.queryForObject("""
        SELECT COUNT(DISTINCT user_id) AS cnt
        FROM messages
        WHERE created_at BETWEEN ? AND ?
        """, Long.class, ts(start), ts(end)),
        new TypeReference<>() {});
    return val == null ? 0 : val;
  }

  public List<RoomActivity> getRoomsForUser(String userId) {
    String key = "roomsForUser:" + userId;
    return cached(key, userHistoryTtl, () -> jdbcTemplate.query("""
        SELECT room_id, MAX(created_at) AS last_activity
        FROM messages
        WHERE user_id = ?
        GROUP BY room_id
        ORDER BY last_activity DESC
        """, ROOM_ACTIVITY_MAPPER, userId),
        new TypeReference<>() {});
  }

  public List<TimeBucketCount> messagesPerMinute(Instant start, Instant end) {
    String key = "perMinute:" + start + ":" + end;
    return cached(key, aggregationTtl, () -> {
      Instant now = Instant.now();
      if (canUsePerMinuteView(start, end, now)) {
        return jdbcTemplate.query("""
            SELECT bucket, message_count AS cnt
            FROM mv_messages_per_minute
            WHERE bucket BETWEEN date_trunc('minute', CAST(? AS timestamptz))
                AND date_trunc('minute', CAST(? AS timestamptz))
            ORDER BY bucket
            """, BUCKET_MAPPER, ts(start), ts(end));
      }
      return jdbcTemplate.query("""
          SELECT date_trunc('minute', created_at) AS bucket, COUNT(*) AS cnt
          FROM messages
          WHERE created_at BETWEEN ? AND ?
          GROUP BY bucket
          ORDER BY bucket
          """, BUCKET_MAPPER, ts(start), ts(end));
    }, new TypeReference<>() {});
  }

  public List<TimeBucketCount> messagesPerSecond(Instant start, Instant end) {
    String key = "perSecond:" + start + ":" + end;
    return cached(key, aggregationTtl, () -> jdbcTemplate.query("""
        SELECT date_trunc('second', created_at) AS bucket, COUNT(*) AS cnt
        FROM messages
        WHERE created_at BETWEEN ? AND ?
        GROUP BY bucket
        ORDER BY bucket
        """, BUCKET_MAPPER, ts(start), ts(end)),
        new TypeReference<>() {});
  }

  public List<IdCount> topUsers(Instant start, Instant end, int limit) {
    String key = "topUsers:" + start + ":" + end + ":" + limit;
    return cached(key, aggregationTtl, () -> {
      Instant now = Instant.now();
      if (canUseRolling24HourAggregateView(start, end, now)) {
        return jdbcTemplate.query("""
            SELECT user_id AS id, message_count AS cnt
            FROM mv_top_users
            ORDER BY message_count DESC, user_id
            LIMIT ?
            """, ID_COUNT_MAPPER, limit);
      }
      return jdbcTemplate.query("""
          SELECT user_id AS id, COUNT(*) AS cnt
          FROM messages
          WHERE created_at BETWEEN ? AND ?
          GROUP BY user_id
          ORDER BY cnt DESC
          LIMIT ?
          """, ID_COUNT_MAPPER, ts(start), ts(end), limit);
    }, new TypeReference<>() {});
  }

  public List<IdCount> topRooms(Instant start, Instant end, int limit) {
    String key = "topRooms:" + start + ":" + end + ":" + limit;
    return cached(key, aggregationTtl, () -> {
      Instant now = Instant.now();
      if (canUseRolling24HourAggregateView(start, end, now)) {
        return jdbcTemplate.query("""
            SELECT room_id AS id, message_count AS cnt
            FROM mv_top_rooms
            ORDER BY message_count DESC, room_id
            LIMIT ?
            """, ID_COUNT_MAPPER, limit);
      }
      return jdbcTemplate.query("""
          SELECT room_id AS id, COUNT(*) AS cnt
          FROM messages
          WHERE created_at BETWEEN ? AND ?
          GROUP BY room_id
          ORDER BY cnt DESC
          LIMIT ?
          """, ID_COUNT_MAPPER, ts(start), ts(end), limit);
    }, new TypeReference<>() {});
  }

  public List<IdCount> participationByRoom(String userId, Instant start, Instant end) {
    String key = "participation:" + userId + ":" + start + ":" + end;
    return cached(key, userHistoryTtl, () -> jdbcTemplate.query("""
        SELECT room_id AS id, COUNT(*) AS cnt
        FROM messages
        WHERE user_id = ? AND created_at BETWEEN ? AND ?
        GROUP BY room_id
        ORDER BY cnt DESC
        """, ID_COUNT_MAPPER, userId, ts(start), ts(end)),
        new TypeReference<>() {});
  }

  /**
   * Fetches from Redis if a non-expired entry exists; otherwise calls the loader,
   * stores the result as JSON with the given TTL, and returns it.
   */
  private <T> T cached(String key, Duration ttl, Supplier<T> loader, TypeReference<T> typeRef) {
    try {
      String json = redisTemplate.opsForValue().get(key);
      if (json != null) {
        return objectMapper.readValue(json, typeRef);
      }
    } catch (JsonProcessingException e) {
      log.warn("[ANALYTICS_CACHE] deserialize failed key={}: {}", key, e.getMessage());
    }

    T value = loader.get();

    try {
      redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
    } catch (JsonProcessingException e) {
      log.warn("[ANALYTICS_CACHE] serialize failed key={}: {}", key, e.getMessage());
    }

    return value;
  }

  private static Timestamp ts(Instant instant) {
    return Timestamp.from(instant);
  }

  private boolean canUsePerMinuteView(Instant start, Instant end, Instant now) {
    Instant cutoff = now.minus(MATERIALIZED_VIEW_WINDOW);
    return !start.isBefore(cutoff) && !end.isAfter(now);
  }

  private boolean canUseRolling24HourAggregateView(Instant start, Instant end, Instant now) {
    Instant cutoff = now.minus(MATERIALIZED_VIEW_WINDOW);
    return isWithinTolerance(start, cutoff) && isWithinTolerance(end, now);
  }

  private boolean isWithinTolerance(Instant actual, Instant expected) {
    return Duration.between(actual, expected).abs().compareTo(VIEW_RANGE_TOLERANCE) <= 0;
  }

  private static Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
