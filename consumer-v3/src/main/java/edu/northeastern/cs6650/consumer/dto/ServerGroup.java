package edu.northeastern.cs6650.consumer.dto;

import java.util.ArrayList;
import java.util.List;

public class ServerGroup {
  private final String serverUrl;
  private final List<String> sessionIds = new ArrayList<>();

  public ServerGroup(String serverUrl) {
    this.serverUrl = serverUrl;
  }

  public void addSession(String id) {
    sessionIds.add(id);
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public List<String> getSessionIds() {
    return sessionIds;
  }
}
