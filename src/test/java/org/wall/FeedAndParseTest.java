package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

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
  @DisplayName("the feed lists posts newest-first")
  void newestFirst() {
    WallPost older = new WallPost("a", "first", "2026-06-30T10:00:00Z");
    WallPost newer = new WallPost("b", "second", "2026-06-30T12:00:00Z");
    WallPost middle = new WallPost("c", "third", "2026-06-30T11:00:00Z");

    assertThat(Feed.newestFirst(List.of(older, newer, middle)))
        .extracting(WallPost::message)
        .containsExactly("second", "third", "first");
  }
}
