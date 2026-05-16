package edu.northeastern.cs6650.consumer.controller;

import edu.northeastern.cs6650.consumer.dto.SessionRef;
import edu.northeastern.cs6650.consumer.dto.SessionRegistration;
import edu.northeastern.cs6650.consumer.service.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/session")
public class RegistrationController {

  private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);
  private static final String SECRET_HEADER = "X-Internal-Secret";

  private final SessionRegistry registry;

  @Value("${internal.secret:secret}")
  private String internalSecret;

  public RegistrationController(SessionRegistry registry) {
    this.registry = registry;
  }

  @PostMapping("/register")
  public ResponseEntity<String> register(
      @RequestHeader(SECRET_HEADER) String secret,
      @RequestBody SessionRegistration req) {

    if (!internalSecret.equals(secret)) {
      return ResponseEntity.status(403).body("Forbidden");
    }

    registry.register(
        req.getRoomId(),
        new SessionRef(req.getServerId(), req.getSessionId(), req.getServerUrl()),
        null,
        null
    );

    log.debug("[REGISTER] session={} room={} server={}",
        req.getSessionId(), req.getRoomId(), req.getServerId());
    return ResponseEntity.ok("registered");
  }

  @PostMapping("/update-user")
  public ResponseEntity<String> updateUser(
      @RequestHeader(SECRET_HEADER) String secret,
      @RequestBody SessionRegistration req) {

    if (!internalSecret.equals(secret)) {
      return ResponseEntity.status(403).body("Forbidden");
    }

    registry.updateUser(req.getSessionId(), req.getUserId(), req.getUsername());

    log.debug("[UPDATE_USER] session={} userId={}", req.getSessionId(), req.getUserId());
    return ResponseEntity.ok("updated");
  }

  @DeleteMapping("/unregister/{sessionId}")
  public ResponseEntity<String> unregister(
      @RequestHeader(SECRET_HEADER) String secret,
      @PathVariable String sessionId) {

    if (!internalSecret.equals(secret)) {
      return ResponseEntity.status(403).body("Forbidden");
    }

    registry.unregister(sessionId);
    log.debug("[UNREGISTER] session={}", sessionId);
    return ResponseEntity.ok("unregistered");
  }
}