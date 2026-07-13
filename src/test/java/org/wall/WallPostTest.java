package org.wall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Wall post")
class WallPostTest {

  @Test
  @DisplayName("create stamps the timestamp")
  void create() {
    WallPost p = WallPost.create("bob", "hi", Instant.parse("2026-06-30T12:00:00Z"));
    assertThat(p.author()).isEqualTo("bob");
    assertThat(p.message()).isEqualTo("hi");
    assertThat(p.timestamp()).isEqualTo("2026-06-30T12:00:00Z");
  }

  @Test
  @DisplayName("rejects an empty message")
  void rejectsEmptyMessage() {
    assertThatThrownBy(() -> new WallPost("bob", "", "2026-06-30T12:00:00Z"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("rejects an author longer than 64 bytes")
  void rejectsLongAuthor() {
    assertThatThrownBy(() -> new WallPost("x".repeat(65), "hi", "2026-06-30T12:00:00Z"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("treats a null author as empty")
  void nullAuthorEmpty() {
    assertThat(new WallPost(null, "hi", "2026-06-30T12:00:00Z").author()).isEmpty();
  }

  @Test
  @DisplayName("carries a tx hash, defaulting to empty when unknown")
  void txHash() {
    assertThat(new WallPost("bob", "hi", "2026-06-30T12:00:00Z").txHash()).isEmpty();
    assertThat(new WallPost("bob", "hi", "2026-06-30T12:00:00Z", "deadbeef").txHash())
        .isEqualTo("deadbeef");
  }

  @Test
  @DisplayName("carries a verified payer address, defaulting to empty when unresolved")
  void address() {
    assertThat(new WallPost("bob", "hi", "2026-06-30T12:00:00Z").address()).isEmpty();
    assertThat(new WallPost("bob", "hi", "2026-06-30T12:00:00Z", "tx").address()).isEmpty();
    assertThat(new WallPost("bob", "hi", "2026-06-30T12:00:00Z", "tx", "addr_test1qxyz").address())
        .isEqualTo("addr_test1qxyz");
  }

  @Test
  @DisplayName("carries tip + pinned, defaulting to 0 / false")
  void tipAndPinned() {
    WallPost none = new WallPost("a", "hi", "2026-06-30T12:00:00Z");
    assertThat(none.tipLovelace()).isZero();
    assertThat(none.pinned()).isFalse();
    WallPost tipped =
        new WallPost("a", "hi", "2026-06-30T12:00:00Z", "tx", "addr", 5_000_000L, true);
    assertThat(tipped.tipLovelace()).isEqualTo(5_000_000L);
    assertThat(tipped.pinned()).isTrue();
  }

  @Test
  @DisplayName("carries an optional pin colour, defaulting to empty")
  void color() {
    assertThat(new WallPost("a", "hi", "2026-06-30T12:00:00Z").color()).isEmpty();
    WallPost c = new WallPost("a", "hi", "2026-06-30T12:00:00Z", "tx", "addr", 0L, false, "mint");
    assertThat(c.color()).isEqualTo("mint");
  }
}
