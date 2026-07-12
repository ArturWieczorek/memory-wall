package org.wall;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects a client that makes too many API requests per minute (HTTP 429), so a public, home-hosted
 * backend cannot be trivially hammered. The health check is exempt (the UI polls it).
 *
 * <p>Behind a tunnel/proxy (Cloudflare, Tailscale Funnel, nginx) the socket IP is the proxy's, so
 * the real client IP arrives in the {@code X-Forwarded-For} header - we use its first hop when
 * present, else the socket address.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final boolean enabled;
  private final RateLimiter limiter;

  public RateLimitFilter(WallProperties props) {
    this.enabled = props.rateLimit().enabled();
    this.limiter = new RateLimiter(props.rateLimit().requestsPerMinute());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    boolean limited = enabled && path.startsWith("/api/") && !path.equals("/api/health");
    if (limited && !limiter.allow(clientIp(req), System.currentTimeMillis())) {
      res.setStatus(429); // Too Many Requests
      res.setContentType("text/plain;charset=UTF-8");
      res.getWriter().write("rate limit exceeded - please slow down");
      return;
    }
    chain.doFilter(req, res);
  }

  /** The real client IP: first hop of X-Forwarded-For if present, else the socket address. */
  static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
