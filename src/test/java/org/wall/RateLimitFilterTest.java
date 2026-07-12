package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rate limit client-IP resolution")
class RateLimitFilterTest {

  @Test
  @DisplayName("with no trusted header configured, uses the socket address")
  void defaultUsesRemoteAddr() {
    // Even if a (spoofable) X-Forwarded-For is present, it is ignored when no header is configured.
    Map<String, String> headers = Map.of("X-Forwarded-For", "1.2.3.4");
    assertThat(RateLimitFilter.resolveClientIp("", headers::get, "10.0.0.9")).isEqualTo("10.0.0.9");
  }

  @Test
  @DisplayName("with a trusted header configured and present, uses that header")
  void usesConfiguredHeader() {
    Map<String, String> headers = Map.of("CF-Connecting-IP", "203.0.113.7");
    assertThat(RateLimitFilter.resolveClientIp("CF-Connecting-IP", headers::get, "10.0.0.9"))
        .isEqualTo("203.0.113.7");
  }

  @Test
  @DisplayName("with a trusted header configured but absent, falls back to the socket address")
  void fallsBackWhenHeaderMissing() {
    assertThat(RateLimitFilter.resolveClientIp("CF-Connecting-IP", h -> null, "10.0.0.9"))
        .isEqualTo("10.0.0.9");
  }
}
