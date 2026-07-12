package org.wall;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code wall.*} settings from {@code application.yml}. All have safe defaults so the app runs
 * with no extra config; each can be overridden by env vars (see {@code application.yml}).
 *
 * @param backendUrl base URL of the Cardano backend (Blockfrost-compatible)
 * @param corsAllowedOrigins browser origins allowed to call the API (the hosted UI); "*" allows any
 * @param rateLimit per-IP request cap that protects the (home-hosted) box from abuse
 * @param blocklist case-insensitive substrings; a post whose author or message contains one is
 *     hidden from the feed (display-side moderation - it cannot remove anything from the chain)
 */
@ConfigurationProperties(prefix = "wall")
public record WallProperties(
    String backendUrl,
    List<String> corsAllowedOrigins,
    RateLimit rateLimit,
    List<String> blocklist) {

  public WallProperties {
    corsAllowedOrigins =
        corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()
            ? List.of("*")
            : List.copyOf(corsAllowedOrigins);
    rateLimit = rateLimit == null ? new RateLimit(true, 20) : rateLimit;
    blocklist = blocklist == null ? List.of() : List.copyOf(blocklist);
  }

  /**
   * @param enabled turn the per-IP limit on/off
   * @param requestsPerMinute allowed API requests per client IP per minute (writes especially)
   */
  public record RateLimit(boolean enabled, int requestsPerMinute) {
    public RateLimit {
      if (requestsPerMinute <= 0) {
        requestsPerMinute = 20;
      }
    }
  }
}
