package edu.northeastern.cs6650.consumer.dto;

public class SessionRegistration {
  private String serverId;
  private String sessionId;
  private String serverUrl;
  private String roomId;
  private String userId;
  private String username;

  public SessionRegistration() {
  }

  public String getServerId() {
    return serverId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getServerUrl() {
    return serverUrl;
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

  public void setServerId(String v) {
    this.serverId = v;
  }

  public void setSessionId(String v) {
    this.sessionId = v;
  }

  public void setServerUrl(String v) {
    this.serverUrl = v;
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
}