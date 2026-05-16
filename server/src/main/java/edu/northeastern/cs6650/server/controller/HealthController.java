package edu.northeastern.cs6650.server.controller;

import edu.northeastern.cs6650.server.handler.ChatWebSocketHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final Instant startTime = Instant.now();
  private final ChatWebSocketHandler handler;

  public HealthController(ChatWebSocketHandler handler) {
    this.handler = handler;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    long s = Duration.between(startTime, Instant.now()).getSeconds();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "UP");
    body.put("timestamp", Instant.now().toString());
    body.put("uptime", String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
    body.putAll(handler.getStatistics());
    return ResponseEntity.ok(body);
  }
}