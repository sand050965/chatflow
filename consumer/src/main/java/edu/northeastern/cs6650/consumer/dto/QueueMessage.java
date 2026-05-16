package edu.northeastern.cs6650.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
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
    this.messageId = v;
  }

  public void setRoomId(String v) {
    this.roomId = v;
  }

  public void setUserId(String v) {
    this.userId = v;
  }

  public void setUsername(String v) {
    this.username = v;
  }

  public void setMessage(String v) {
    this.message = v;
  }

  public void setTimestamp(String v) {
    this.timestamp = v;
  }

  public void setMessageType(String v) {
    this.messageType = v;
  }

  public void setServerId(String v) {
    this.serverId = v;
  }

  public void setClientIp(String v) {
    this.clientIp = v;
  }
}
