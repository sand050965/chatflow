package edu.northeastern.cs6650.consumer.service;

import edu.northeastern.cs6650.consumer.dto.QueueMessage;
import edu.northeastern.cs6650.consumer.dto.UserInfo;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RoomManager {

  private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

  private final ConcurrentHashMap<String, Set<String>> roomUsers =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, UserInfo> activeUsers =
      new ConcurrentHashMap<>();

  public void handleJoin(QueueMessage msg) {
    roomUsers
        .computeIfAbsent(msg.getRoomId(), k -> ConcurrentHashMap.newKeySet())
        .add(msg.getUserId());
    activeUsers.put(msg.getUserId(),
        new UserInfo(msg.getUserId(), msg.getUsername(), msg.getRoomId()));
    log.debug("[ROOM_JOIN] userId={} room={} totalInRoom={}",
        msg.getUserId(), msg.getRoomId(),
        roomUsers.get(msg.getRoomId()).size());
  }

  public void handleLeave(QueueMessage msg) {
    Set<String> users = roomUsers.get(msg.getRoomId());
    if (users != null) {
      users.remove(msg.getUserId());
      if (users.isEmpty()) {
        roomUsers.remove(msg.getRoomId());
      }
    }
    UserInfo info = activeUsers.remove(msg.getUserId());
    if (info != null) {
      log.debug("[ROOM_LEAVE] userId={} room={} msgCount={}",
          msg.getUserId(), msg.getRoomId(), info.getMessageCount());
    }
  }

  public void recordMessage(String userId) {
    UserInfo info = activeUsers.get(userId);
    if (info != null) {
      info.incrementMessageCount();
    }
  }

  public boolean hasActiveUsers(String roomId) {
    Set<String> users = roomUsers.get(roomId);
    return users != null && !users.isEmpty();
  }

  public Set<String> getUsersInRoom(String roomId) {
    return roomUsers.getOrDefault(roomId, Set.of());
  }

  public int getActiveUserCount() {
    return activeUsers.size();
  }

  public int getActiveRoomCount() {
    return roomUsers.size();
  }

  public Map<String, Integer> getRoomStats() {
    Map<String, Integer> stats = new LinkedHashMap<>();
    roomUsers.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> stats.put(e.getKey(), e.getValue().size()));
    return stats;
  }
}