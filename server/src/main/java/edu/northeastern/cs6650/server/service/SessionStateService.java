package edu.northeastern.cs6650.server.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SessionStateService {

  private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();


  public void activate(String sessionId) {
    activeSessions.add(sessionId);
  }

  public void deactivate(String sessionId) {
    activeSessions.remove(sessionId);
  }

  public boolean isActive(String sessionId) {
    return activeSessions.contains(sessionId);
  }
}
