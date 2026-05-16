package edu.northeastern.cs6650.consumer.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreaker {
  private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

  public enum State { CLOSED, OPEN, HALF_OPEN }

  private final String name;
  private final int failureThreshold;
  private final long cooldownMs;
  private final int halfOpenMaxCalls;

  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
  private final AtomicLong openedAt = new AtomicLong(0);

  public CircuitBreaker(String name, int failureThreshold, long cooldownMs, int halfOpenMaxCalls) {
    this.name = name;
    this.failureThreshold = failureThreshold;
    this.cooldownMs = cooldownMs;
    this.halfOpenMaxCalls = halfOpenMaxCalls;
  }

  public boolean allowRequest() {
    State current = state.get();
    if (current == State.OPEN) {
      if (System.currentTimeMillis() - openedAt.get() < cooldownMs) {
        log.warn("[CIRCUIT_BREAKER] {} is OPEN, rejecting request", name);
        return false;
      }
      if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
        halfOpenCalls.set(0);
        log.info("[CIRCUIT_BREAKER] {} → HALF_OPEN, testing...", name);
      }
    }

    if (state.get() == State.HALF_OPEN) {
      if (halfOpenCalls.getAndIncrement() >= halfOpenMaxCalls) {
        log.warn("[CIRCUIT_BREAKER] {} HALF_OPEN quota exceeded, rejecting", name);
        return false;
      }
    }

    return true;
  }

  public void onSuccess() {
    State current = state.get();
    if (current == State.HALF_OPEN) {
      state.set(State.CLOSED);
      failureCount.set(0);
      log.info("[CIRCUIT_BREAKER] {} → CLOSED (recovered)", name);
    } else if (current == State.CLOSED) {
      failureCount.set(0);
    }
  }

  public void onFailure() {
    State current = state.get();
    if (current == State.HALF_OPEN) {
      open();
      return;
    }
    int failures = failureCount.incrementAndGet();
    log.warn("[CIRCUIT_BREAKER] {} failure {}/{}", name, failures, failureThreshold);
    if (failures >= failureThreshold) {
      open();
    }
  }

  private void open() {
    state.set(State.OPEN);
    openedAt.set(System.currentTimeMillis());
    failureCount.set(0);
    log.error("[CIRCUIT_BREAKER] {} → OPEN (cooldown {}ms)", name, cooldownMs);
  }

  public State getState() { return state.get(); }
  public boolean isOpen() { return state.get() == State.OPEN; }
}
