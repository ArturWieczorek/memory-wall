package org.wall;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects a client that makes too many API requests per minute (HTTP 429), so a public, home-hosted
 * backend cannot be trivially hammered. The health check is exempt (the UI polls it).
 *
 * <p>Identifying the client: by default we use the socket address ({@code getRemoteAddr}). Behind a
 * trusted proxy/tunnel set {@code wall.rate-limit.client-ip-header} to the header that proxy stamps
 * with the real client IP (e.g. {@code CF-Connecting-IP} behind Cloudflare). We deliberately do NOT
 * trust the first hop of {@code X-Forwarded-For}: a client can put any value there and rotate it to
 * get a fresh bucket per request, defeating the limit. Pair this with binding the server to
 * localhost (see application.yml) so it is only reachable through the tunnel.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final boolean enabled;
  private final String clientIpHeader;
  private final RateLimiter limiter;

  public RateLimitFilter(WallProperties props) {
    this.enabled = props.rateLimit().enabled();
    this.clientIpHeader = props.rateLimit().clientIpHeader();
    this.limiter = new RateLimiter(props.rateLimit().requestsPerMinute());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    boolean limited = enabled && path.startsWith("/api/") && !path.equals("/api/health");
    if (limited) {
      String ip = resolveClientIp(clientIpHeader, req::getHeader, req.getRemoteAddr());
      if (!limiter.allow(ip, System.currentTimeMillis())) {
        res.setStatus(429); // Too Many Requests
        res.setContentType("text/plain;charset=UTF-8");
        res.getWriter().write("rate limit exceeded - please slow down");
        return;
      }
    }
    chain.doFilter(req, res);
  }

  /**
   * Resolve the client IP: if a trusted header is configured and present, use it; otherwise the
   * socket address. Pure and testable.
   */
  static String resolveClientIp(
      String headerName, Function<String, String> headerLookup, String remoteAddr) {
    if (headerName != null && !headerName.isBlank()) {
      String v = headerLookup.apply(headerName);
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return remoteAddr;
  }
}
