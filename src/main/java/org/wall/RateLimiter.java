package org.wall;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tiny in-memory, per-key fixed-window rate limiter: each key (a client IP) may make up to {@code
 * perMinute} requests within a one-minute window; the count resets when a new minute starts.
 *
 * <p>Pure and deterministic - the current time is passed in - so it is easy to unit-test without
 * sleeping. Good enough to protect a home-hosted box from casual abuse; it is not a distributed or
 * exact limiter (each server instance counts on its own).
 */
public final class RateLimiter {

  private final int perMinute;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimiter(int perMinute) {
    this.perMinute = perMinute;
  }

  /** One key's current minute-window and how many requests it has used. */
  private static final class Window {
    long minute;
    int count;
  }

  /**
   * Record a request from {@code key} at {@code nowMillis} and say whether it is allowed.
   * Thread-safe per key.
   */
  public boolean allow(String key, long nowMillis) {
    long minute = nowMillis / 60_000L;
    Window w = windows.computeIfAbsent(key, k -> new Window());
    synchronized (w) {
      if (w.minute != minute) { // a new minute -> reset
        w.minute = minute;
        w.count = 0;
      }
      if (w.count >= perMinute) {
        return false;
      }
      w.count++;
      return true;
    }
  }
}
