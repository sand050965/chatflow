package edu.northeastern.cs6650.server.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-key sliding window rate limiter.
 * Allows up to maxRequests within windowMs milliseconds.
 */
public class RateLimiter {

  private final int maxRequests;
  private final long windowMs;
  private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

  public RateLimiter(int maxRequests, long windowMs) {
    this.maxRequests = maxRequests;
    this.windowMs = windowMs;
  }

  /**
   * Returns true if the request is allowed, false if rate limit exceeded.
   */
  public boolean allowRequest(String key) {
    long now = System.currentTimeMillis();
    long[] state = windows.computeIfAbsent(key, k -> new long[]{now, 0});

    synchronized (state) {
      if (now - state[0] >= windowMs) {
        state[0] = now;
        state[1] = 1;
        return true;
      }
      if (state[1] < maxRequests) {
        state[1]++;
        return true;
      }
      return false;
    }
  }

  public void evict(String key) {
    windows.remove(key);
  }
}
