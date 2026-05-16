package edu.northeastern.cs6650.server.dto;

import java.util.UUID;

public class QueueMessage {
  private String messageId;
  private String roomId;
  private String userId;
  private String username;
  private String message;
  private String timestamp;
  private String messageType;
  private String serverId;
  private String clientIp;

  public QueueMessage() {
  }

  public static QueueMessage from(ChatMessage msg, String roomId,
                                  String serverId, String clientIp) {
    QueueMessage q = new QueueMessage();
    q.messageId = UUID.randomUUID().toString();
    q.roomId = roomId;
    q.userId = msg.getUserId();
    q.username = msg.getUsername();
    q.message = msg.getMessage();
    q.timestamp = msg.getTimestamp();
    q.messageType = msg.getMessageType();
    q.serverId = serverId;
    q.clientIp = clientIp;
    return q;
  }

  // getters / setters
  public String getMessageId() {
    return messageId;
  }

  public String getRoomId() {
    return roomId;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getMessage() {
    return message;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public String getMessageType() {
    return messageType;
  }

  public String getServerId() {
    return serverId;
  }

  public String getClientIp() {
    return clientIp;
  }

  public void setMessageId(String v) {
    messageId = v;
  }

  public void setRoomId(String v) {
    roomId = v;
  }

  public void setUserId(String v) {
    userId = v;
  }

  public void setUsername(String v) {
    username = v;
  }

  public void setMessage(String v) {
    message = v;
  }

  public void setTimestamp(String v) {
    timestamp = v;
  }

  public void setMessageType(String v) {
    messageType = v;
  }

  public void setServerId(String v) {
    serverId = v;
  }

  public void setClientIp(String v) {
    clientIp = v;
  }
}
