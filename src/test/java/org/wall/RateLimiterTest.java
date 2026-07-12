package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rate limiter")
class RateLimiterTest {

  private static final long MIN1 = 60_000L; // start of one minute (ms since epoch)
  private static final long MIN2 = 120_000L; // the next minute

  @Test
  @DisplayName("allows up to the limit within a minute, then blocks")
  void allowsThenBlocks() {
    RateLimiter rl = new RateLimiter(3);
    assertThat(rl.allow("ip", MIN1)).isTrue();
    assertThat(rl.allow("ip", MIN1)).isTrue();
    assertThat(rl.allow("ip", MIN1)).isTrue();
    assertThat(rl.allow("ip", MIN1)).isFalse(); // 4th in the same minute
  }

  @Test
  @DisplayName("resets the count when a new minute starts")
  void resetsNextMinute() {
    RateLimiter rl = new RateLimiter(1);
    assertThat(rl.allow("ip", MIN1)).isTrue();
    assertThat(rl.allow("ip", MIN1)).isFalse();
    assertThat(rl.allow("ip", MIN2)).isTrue(); // new window
  }

  @Test
  @DisplayName("tracks each key (IP) independently")
  void perKey() {
    RateLimiter rl = new RateLimiter(1);
    assertThat(rl.allow("a", MIN1)).isTrue();
    assertThat(rl.allow("b", MIN1)).isTrue(); // different IP, own budget
    assertThat(rl.allow("a", MIN1)).isFalse();
  }
}
