package edu.northeastern.cs6650.consumer.controller;

import edu.northeastern.cs6650.consumer.dto.IdCount;
import edu.northeastern.cs6650.consumer.dto.MessageRecord;
import edu.northeastern.cs6650.consumer.dto.RoomActivity;
import edu.northeastern.cs6650.consumer.dto.TimeBucketCount;
import edu.northeastern.cs6650.consumer.service.AnalyticsService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/metrics/analytics")
  public ResponseEntity<Map<String, Object>> analytics(
      @RequestParam(defaultValue = "1") String roomId,
      @RequestParam(defaultValue = "1") String userId,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime,
      @RequestParam(defaultValue = "5") int topN,
      @RequestParam(defaultValue = "1000") int limit
  ) {
    Instant now = Instant.now();
    Instant start = parseInstantOrDefault(startTime, now.minusSeconds(3600));
    Instant end = parseInstantOrDefault(endTime, now);

    List<MessageRecord> roomMessages =
        analyticsService.getMessagesForRoom(roomId, start, end, limit);
    List<MessageRecord> userHistory =
        analyticsService.getUserHistory(userId, start, end, limit);
    long activeUsers = analyticsService.countActiveUsers(start, end);
    List<RoomActivity> roomsForUser =
        analyticsService.getRoomsForUser(userId);

    List<TimeBucketCount> perMinute =
        analyticsService.messagesPerMinute(start, end);
    List<TimeBucketCount> perSecond =
        analyticsService.messagesPerSecond(start, end);
    List<IdCount> topUsers =
        analyticsService.topUsers(start, end, topN);
    List<IdCount> topRooms =
        analyticsService.topRooms(start, end, topN);
    List<IdCount> participation =
        analyticsService.participationByRoom(userId, start, end);

    Map<String, Object> resp = new LinkedHashMap<>();
    Map<String, Object> core = new LinkedHashMap<>();
    Map<String, Object> analytics = new LinkedHashMap<>();

    core.put("roomId", roomId);
    core.put("userId", userId);
    core.put("startTime", start);
    core.put("endTime", end);
    core.put("messagesForRoom", roomMessages);
    core.put("userHistory", userHistory);
    core.put("activeUsers", activeUsers);
    core.put("roomsForUser", roomsForUser);

    analytics.put("messagesPerMinute", perMinute);
    analytics.put("messagesPerSecond", perSecond);
    analytics.put("topUsers", topUsers);
    analytics.put("topRooms", topRooms);
    analytics.put("participationByRoom", participation);

    resp.put("coreQueries", core);
    resp.put("analytics", analytics);
    return ResponseEntity.ok(resp);
  }

  private Instant parseInstantOrDefault(String value, Instant fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Instant.parse(value);
    } catch (Exception ignored) {
      return fallback;
    }
  }
}
