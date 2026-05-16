package edu.northeastern.cs6650.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatMessage {

  @JsonProperty("userId")
  private String userId;

  @JsonProperty("username")
  private String username;

  @JsonProperty("message")
  private String message;

  @JsonProperty("timestamp")
  private String timestamp;

  @JsonProperty("messageType")
  private String messageType;

  public ChatMessage() {
  }

  public ChatMessage(String userId, String username, String message,
                     String timestamp, String messageType) {
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.timestamp = timestamp;
    this.messageType = messageType;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getMessageType() {
    return messageType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }
}