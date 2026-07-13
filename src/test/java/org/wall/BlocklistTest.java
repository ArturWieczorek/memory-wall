package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Blocklist (display-side moderation)")
class BlocklistTest {

  private static Blocklist withTerms(String... terms) {
    return new Blocklist(
        new WallProperties(
            null,
            null,
            null,
            null,
            List.of(terms),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  private static Blocklist withTxHashes(String... hashes) {
    return new Blocklist(
        new WallProperties(
            null,
            null,
            null,
            null,
            List.of(),
            List.of(hashes),
            null,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  private static WallPost post(String author, String message) {
    return new WallPost(author, message, "2026-06-30T12:00:00Z");
  }

  private static WallPost postWithTx(String txHash) {
    return new WallPost("a", "hi", "2026-06-30T12:00:00Z", txHash);
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
  @DisplayName("hides a post whose tx hash is blocked (exact, case-insensitive); leaves others")
  void blocksByTxHash() {
    Blocklist b = withTxHashes("ABC123");
    assertThat(b.isBlocked(postWithTx("abc123"))).isTrue(); // exact match, case-insensitive
    assertThat(b.isBlocked(postWithTx("def456"))).isFalse(); // a different post
    assertThat(b.isBlocked(post("a", "hi"))).isFalse(); // no tx hash at all
  }

  @Test
  @DisplayName("filter() drops a blocked tx hash and keeps the rest")
  void filtersByTxHash() {
    Blocklist b = withTxHashes("bad0");
    WallPost good1 = postWithTx("good1");
    WallPost bad = postWithTx("bad0");
    WallPost good2 = postWithTx("good2");
    assertThat(b.filter(List.of(good1, bad, good2))).containsExactly(good1, good2);
  }

  @Test
  @DisplayName("an empty blocklist hides nothing")
  void emptyBlocksNothing() {
    Blocklist b =
        new Blocklist(
            new WallProperties(
                null, null, null, null, null, null, null, null, null, null, null, null, null));
    assertThat(b.isBlocked(post("a", "anything at all"))).isFalse();
    assertThat(b.isBlocked(postWithTx("abc123"))).isFalse();
    List<WallPost> posts = List.of(post("a", "one"), post("b", "two"));
    assertThat(b.filter(posts)).isEqualTo(posts);
  }
}
