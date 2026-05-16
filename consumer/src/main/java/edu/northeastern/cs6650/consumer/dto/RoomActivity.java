package edu.northeastern.cs6650.consumer.dto;

import java.time.Instant;

public class RoomActivity {
  private String roomId;
  private Instant lastActivity;

  public RoomActivity() {
  }

  public RoomActivity(String roomId, Instant lastActivity) {
    this.roomId = roomId;
    this.lastActivity = lastActivity;
  }

  public String getRoomId() { return roomId; }
  public Instant getLastActivity() { return lastActivity; }
  public void setRoomId(String roomId) { this.roomId = roomId; }
  public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
}
