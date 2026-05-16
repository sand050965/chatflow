package edu.northeastern.cs6650.consumer.service;

import edu.northeastern.cs6650.consumer.dto.ServerGroup;
import edu.northeastern.cs6650.consumer.dto.SessionRef;
import edu.northeastern.cs6650.consumer.dto.UserInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SessionRegistry {

  private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

  private final ConcurrentHashMap<String, Set<SessionRef>> roomSessions =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, UserInfo> activeUsers =
      new ConcurrentHashMap<>();

  public final AtomicLong messagesProcessed = new AtomicLong();

  private final ConcurrentHashMap<String, String> sessionRoomIndex =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, String> sessionUserIndex =
      new ConcurrentHashMap<>();

  public void register(String roomId, SessionRef ref, String userId, String username) {
    roomSessions
        .computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
        .add(ref);
    sessionRoomIndex.put(ref.getSessionId(), roomId);

    if (userId != null) {
      activeUsers.put(userId, new UserInfo(userId, username, roomId));
      sessionUserIndex.put(ref.getSessionId(), userId);
    }
    log.debug("[SESSION_REG] session={} room={} server={}",
        ref.getSessionId(), roomId, ref.getServerId());
  }

  public void updateUser(String sessionId, String userId, String username) {
    String roomId = sessionRoomIndex.get(sessionId);
    if (roomId == null) {
      return;
    }
    activeUsers.put(userId, new UserInfo(userId, username, roomId));
    sessionUserIndex.put(sessionId, userId);
    log.debug("[SESSION_UPDATE] session={} userId={} room={}", sessionId, userId, roomId);
  }

  public void unregister(String sessionId) {
    String roomId = sessionRoomIndex.remove(sessionId);
    if (roomId != null) {
      Set<SessionRef> refs = roomSessions.get(roomId);
      if (refs != null) {
        refs.removeIf(r -> r.getSessionId().equals(sessionId));
        if (refs.isEmpty()) {
          roomSessions.remove(roomId);
        }
      }
    }
    String userId = sessionUserIndex.remove(sessionId);
    if (userId != null) {
      activeUsers.remove(userId);
    }
    log.debug("[SESSION_UNREG] session={}", sessionId);
  }

  public void recordMessage(String userId) {
    messagesProcessed.incrementAndGet();
    UserInfo info = activeUsers.get(userId);
    if (info != null) {
      info.incrementMessageCount();
    }
  }

  public Map<String, ServerGroup> getGroupedByServer(String roomId) {
    Set<SessionRef> refs = roomSessions.get(roomId);
    if (refs == null || refs.isEmpty()) {
      return Map.of();
    }

    Map<String, ServerGroup> groups = new HashMap<>();
    for (SessionRef ref : refs) {
      groups.computeIfAbsent(ref.getServerId(),
              k -> new ServerGroup(ref.getServerUrl()))
          .addSession(ref.getSessionId());
    }
    return groups;
  }

  public boolean hasActiveSessions(String roomId) {
    Set<SessionRef> refs = roomSessions.get(roomId);
    return refs != null && !refs.isEmpty();
  }

  public int getTotalSessions() {
    return sessionRoomIndex.size();
  }

  public int getActiveRoomCount() {
    return roomSessions.size();
  }

  public int getActiveUserCount() {
    return activeUsers.size();
  }

  public long getMessagesProcessed() {
    return messagesProcessed.get();
  }

  public Map<String, Integer> getRoomStats() {
    Map<String, Integer> stats = new LinkedHashMap<>();
    roomSessions.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> stats.put(e.getKey(), e.getValue().size()));
    return stats;
  }
}