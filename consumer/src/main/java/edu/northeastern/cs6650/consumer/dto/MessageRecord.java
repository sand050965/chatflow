package edu.northeastern.cs6650.consumer.dto;

import java.time.Instant;

public class MessageRecord {
  private String messageId;
  private String roomId;
  private String userId;
  private String username;
  private String message;
  private String messageType;
  private String serverId;
  private String clientIp;
  private Instant createdAt;

  public MessageRecord() {
  }

  public MessageRecord(String messageId, String roomId, String userId, String username,
                       String message, String messageType, String serverId,
                       String clientIp, Instant createdAt) {
    this.messageId = messageId;
    this.roomId = roomId;
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.messageType = messageType;
    this.serverId = serverId;
    this.clientIp = clientIp;
    this.createdAt = createdAt;
  }

  public String getMessageId() { return messageId; }
  public String getRoomId() { return roomId; }
  public String getUserId() { return userId; }
  public String getUsername() { return username; }
  public String getMessage() { return message; }
  public String getMessageType() { return messageType; }
  public String getServerId() { return serverId; }
  public String getClientIp() { return clientIp; }
  public Instant getCreatedAt() { return createdAt; }

  public void setMessageId(String messageId) { this.messageId = messageId; }
  public void setRoomId(String roomId) { this.roomId = roomId; }
  public void setUserId(String userId) { this.userId = userId; }
  public void setUsername(String username) { this.username = username; }
  public void setMessage(String message) { this.message = message; }
  public void setMessageType(String messageType) { this.messageType = messageType; }
  public void setServerId(String serverId) { this.serverId = serverId; }
  public void setClientIp(String clientIp) { this.clientIp = clientIp; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
