package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Feed and parsing")
class FeedAndParseTest {

  @Test
  @DisplayName("a post round-trips through metadata (including a multi-chunk message)")
  void roundTrip() {
    WallPost shortPost = new WallPost("alice", "gm", "2026-06-30T10:00:00Z");
    WallPost longPost = new WallPost("bob", "x".repeat(150), "2026-06-30T11:00:00Z");

    assertThat(Wall.parsePost(Wall.postMap(shortPost))).isEqualTo(shortPost);
    assertThat(Wall.parsePost(Wall.postMap(longPost))).isEqualTo(longPost); // chunks rejoin exactly
  }

  @Test
  @DisplayName("a pin colour round-trips through metadata (the 'c' field)")
  void colorRoundTrip() {
    WallPost coloured =
        new WallPost("carol", "hi", "2026-06-30T10:00:00Z", "", "", 0L, false, "mint");
    assertThat(Wall.parsePost(Wall.postMap(coloured)).color()).isEqualTo("mint");
  }

  @Test
  @DisplayName("the feed lists posts newest-first")
  void newestFirst() {
    WallPost older = new WallPost("a", "first", "2026-06-30T10:00:00Z");
    WallPost newer = new WallPost("b", "second", "2026-06-30T12:00:00Z");
    WallPost middle = new WallPost("c", "third", "2026-06-30T11:00:00Z");

    assertThat(Feed.newestFirst(List.of(older, newer, middle)))
        .extracting(WallPost::message)
        .containsExactly("second", "third", "first");
  }

  // A pinned post (paid the pin fee); the reader would set pinned=true.
  private static WallPost pinned(String msg, String ts, long tip) {
    return new WallPost("a", msg, ts, "tx-" + msg, "", tip, true);
  }

  private static WallPost plain(String msg, String ts) {
    return new WallPost("a", msg, ts, "tx-" + msg, "", 0L, false);
  }

  @Test
  @DisplayName("forDisplay shows active pins first (by tip), then everyone else newest-first")
  void forDisplayPinnedFirst() {
    Instant now = Instant.parse("2026-06-30T13:00:00Z");
    WallPost pinLow = pinned("pin 2 ADA", "2026-06-30T09:00:00Z", 2_000_000L);
    WallPost pinHigh = pinned("pin 5 ADA", "2026-06-30T08:00:00Z", 5_000_000L);
    WallPost newPlain = plain("new plain", "2026-06-30T12:00:00Z");
    WallPost oldPlain = plain("old plain", "2026-06-30T07:00:00Z");

    assertThat(Feed.forDisplay(List.of(newPlain, pinLow, pinHigh, oldPlain), 10, now, 604_800))
        .extracting(WallPost::message)
        // higher tip first among pins, then plain posts newest-first
        .containsExactly("pin 5 ADA", "pin 2 ADA", "new plain", "old plain");
  }

  @Test
  @DisplayName("forDisplay caps pins at maxPinned; the outbid pin is demoted into the feed")
  void forDisplayCap() {
    Instant now = Instant.parse("2026-06-30T13:00:00Z");
    WallPost pinTop = pinned("top", "2026-06-30T09:00:00Z", 9_000_000L);
    WallPost pinMid = pinned("mid", "2026-06-30T10:00:00Z", 5_000_000L);
    WallPost pinLow = pinned("low", "2026-06-30T11:00:00Z", 1_000_000L); // outbid -> demoted

    List<WallPost> out = Feed.forDisplay(List.of(pinTop, pinMid, pinLow), 2, now, 604_800);
    assertThat(out).extracting(WallPost::message).containsExactly("top", "mid", "low");
    assertThat(out)
        .filteredOn(WallPost::pinned)
        .extracting(WallPost::message)
        .containsExactly("top", "mid"); // only the top 2 are actually pinned
  }

  @Test
  @DisplayName("forDisplay expires a pin past its window (it becomes a normal post)")
  void forDisplayExpiry() {
    WallPost oldPin = pinned("stale pin", "2026-06-01T00:00:00Z", 9_000_000L);
    WallPost recent = plain("recent", "2026-06-30T12:00:00Z");
    Instant now = Instant.parse("2026-06-30T13:00:00Z"); // ~29 days later

    // 7-day window: the old pin has expired, so it is not pinned and just sorts by time.
    List<WallPost> out = Feed.forDisplay(List.of(oldPin, recent), 3, now, 604_800);
    assertThat(out).extracting(WallPost::message).containsExactly("recent", "stale pin");
    assertThat(out).filteredOn(WallPost::pinned).isEmpty(); // none active
  }
}
