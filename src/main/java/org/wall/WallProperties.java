package org.wall;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code wall.*} settings from {@code application.yml}. All have safe defaults so the app runs
 * with no extra config; each can be overridden by env vars (see {@code application.yml}).
 *
 * @param backendUrl base URL of the Cardano backend (Blockfrost-compatible)
 * @param backendProjectId provider project id / API key. Ignored by local Yaci DevKit (default
 *     "wall"); MUST be your real key for hosted Blockfrost (preprod/mainnet). A secret - keep it in
 *     env (WALL_BACKEND_PROJECT_ID), never in the repo.
 * @param corsAllowedOrigins browser origins allowed to call the API (the hosted UI); "*" allows any
 * @param rateLimit per-IP request cap that protects the (home-hosted) box from abuse
 * @param blocklist case-insensitive substrings; a post whose author or message contains one is
 *     hidden from the feed (display-side moderation - it cannot remove anything from the chain)
 * @param maxMessageBytes reject a post whose message exceeds this many UTF-8 bytes (anti-DoS)
 * @param maxTxChars reject a submit whose txCbor/witness hex exceeds this many characters
 *     (anti-DoS)
 */
@ConfigurationProperties(prefix = "wall")
public record WallProperties(
    String backendUrl,
    String backendProjectId,
    List<String> corsAllowedOrigins,
    RateLimit rateLimit,
    List<String> blocklist,
    Integer maxMessageBytes,
    Integer maxTxChars) {

  public WallProperties {
    backendProjectId =
        (backendProjectId == null || backendProjectId.isBlank()) ? "wall" : backendProjectId;
    corsAllowedOrigins =
        corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()
            ? List.of("*")
            : List.copyOf(corsAllowedOrigins);
    rateLimit = rateLimit == null ? new RateLimit(true, 20, "") : rateLimit;
    blocklist = blocklist == null ? List.of() : List.copyOf(blocklist);
    maxMessageBytes = (maxMessageBytes == null || maxMessageBytes <= 0) ? 4096 : maxMessageBytes;
    maxTxChars = (maxTxChars == null || maxTxChars <= 0) ? 100_000 : maxTxChars;
  }

  /**
   * @param enabled turn the per-IP limit on/off
   * @param requestsPerMinute allowed API requests per client IP per minute (writes especially)
   * @param clientIpHeader header carrying the real client IP behind a trusted proxy (e.g.
   *     "CF-Connecting-IP" behind Cloudflare). Blank = use the socket address. Do NOT set this to
   *     X-Forwarded-For: a client can spoof its first hop and evade the limit.
   */
  public record RateLimit(boolean enabled, int requestsPerMinute, String clientIpHeader) {
    public RateLimit {
      if (requestsPerMinute <= 0) {
        requestsPerMinute = 20;
      }
      clientIpHeader = clientIpHeader == null ? "" : clientIpHeader;
    }
  }
}
