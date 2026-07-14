package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The SQLite-backed store, exercised against a real temp-file database (no chain, no network). This
 * is the durability guarantee: what we save, we can load back after "restarting" (a fresh store on
 * the same file).
 */
@DisplayName("SqlitePostStore (durable index store)")
class SqlitePostStoreTest {

  private static WallPost post(String tx, String ts) {
    return new WallPost("alice", "hello " + tx, ts, tx, "addr_test1abc", 5_000_000L, true, "sky");
  }

  @Test
  @DisplayName("saves posts and loads them back, newest-first")
  void roundTrip(@TempDir Path dir) {
    Path db = dir.resolve("wall-index.db");
    SqlitePostStore store = new SqlitePostStore(db.toString());

    store.save(List.of(post("tx1", "2026-06-30T10:00:00Z"), post("tx2", "2026-06-30T12:00:00Z")));

    assertThat(store.loadAll())
        .extracting(WallPost::txHash)
        .containsExactly("tx2", "tx1"); // ORDER BY timestamp DESC
    // Round-tripped fields survive.
    WallPost first = store.loadAll().get(0);
    assertThat(first.address()).isEqualTo("addr_test1abc");
    assertThat(first.tipLovelace()).isEqualTo(5_000_000L);
    assertThat(first.pinned()).isTrue();
    assertThat(first.color()).isEqualTo("sky");
  }

  @Test
  @DisplayName("saving the same tx hash twice is idempotent (INSERT OR IGNORE)")
  void idempotentByTxHash(@TempDir Path dir) {
    SqlitePostStore store = new SqlitePostStore(dir.resolve("w.db").toString());

    store.save(List.of(post("tx1", "2026-06-30T10:00:00Z")));
    store.save(List.of(post("tx1", "2026-06-30T10:00:00Z"))); // same key again

    assertThat(store.loadAll()).hasSize(1);
  }

  @Test
  @DisplayName("survives a restart: a new store on the same file sees the saved posts")
  void survivesRestart(@TempDir Path dir) {
    Path db = dir.resolve("w.db");
    new SqlitePostStore(db.toString()).save(List.of(post("tx1", "2026-06-30T10:00:00Z")));

    SqlitePostStore reopened = new SqlitePostStore(db.toString()); // "restart"
    assertThat(reopened.loadAll()).extracting(WallPost::txHash).containsExactly("tx1");
  }

  @Test
  @DisplayName("skips posts without a tx hash (the primary key) instead of failing")
  void skipsPostsWithoutTxHash(@TempDir Path dir) {
    SqlitePostStore store = new SqlitePostStore(dir.resolve("w.db").toString());

    store.save(List.of(new WallPost("a", "no hash yet", "2026-06-30T10:00:00Z")));

    assertThat(store.loadAll()).isEmpty();
  }

  @Test
  @DisplayName("an empty save is a no-op")
  void emptySaveIsNoOp(@TempDir Path dir) {
    SqlitePostStore store = new SqlitePostStore(dir.resolve("w.db").toString());
    store.save(List.of());
    assertThat(store.loadAll()).isEmpty();
  }
}
