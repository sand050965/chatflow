package edu.northeastern.cs6650.consumer.dto;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class UserInfo {

  private final String userId;
  private final String username;
  private final String roomId;
  private final Instant joinedAt;
  private final AtomicLong messageCount = new AtomicLong(0);

  public UserInfo(String userId, String username, String roomId) {
    this.userId = userId;
    this.username = username;
    this.roomId = roomId;
    this.joinedAt = Instant.now();
  }

  public void incrementMessageCount() {
    messageCount.incrementAndGet();
  }

  public long getMessageCount() {
    return messageCount.get();
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getRoomId() {
    return roomId;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }
}