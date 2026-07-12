package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Blocklist (display-side moderation)")
class BlocklistTest {

  private static Blocklist withTerms(String... terms) {
    return new Blocklist(new WallProperties(null, null, null, null, List.of(terms), null, null));
  }

  private static WallPost post(String author, String message) {
    return new WallPost(author, message, "2026-06-30T12:00:00Z");
  }

  @Test
  @DisplayName("hides a post matching a term (case-insensitive), in author or message")
  void matches() {
    Blocklist b = withTerms("spam");
    assertThat(b.isBlocked(post("a", "buy SPAM now"))).isTrue(); // case-insensitive, in message
    assertThat(b.isBlocked(post("spammer", "hi"))).isTrue(); // substring, in author
    assertThat(b.isBlocked(post("a", "hello world"))).isFalse();
  }

  @Test
  @DisplayName("filter() drops blocked posts and keeps order")
  void filters() {
    Blocklist b = withTerms("spam");
    WallPost good1 = post("a", "gm");
    WallPost bad = post("b", "this is spam");
    WallPost good2 = post("c", "hello");
    assertThat(b.filter(List.of(good1, bad, good2))).containsExactly(good1, good2);
  }

  @Test
  @DisplayName("an empty blocklist hides nothing")
  void emptyBlocksNothing() {
    Blocklist b = new Blocklist(new WallProperties(null, null, null, null, null, null, null));
    assertThat(b.isBlocked(post("a", "anything at all"))).isFalse();
    List<WallPost> posts = List.of(post("a", "one"), post("b", "two"));
    assertThat(b.filter(posts)).isEqualTo(posts);
  }
}
