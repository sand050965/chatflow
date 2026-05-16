package edu.northeastern.cs6650.consumer.dto;

import java.util.Objects;

public class SessionRef {
  private final String serverId;
  private final String sessionId;
  private final String serverUrl;

  public SessionRef(String serverId, String sessionId, String serverUrl) {
    this.serverId  = serverId;
    this.sessionId = sessionId;
    this.serverUrl = serverUrl;
  }

  public String getServerId()  { return serverId; }
  public String getSessionId() { return sessionId; }
  public String getServerUrl() { return serverUrl; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SessionRef s)) return false;
    return Objects.equals(sessionId, s.sessionId);
  }

  @Override
  public int hashCode() { return Objects.hash(sessionId); }
}