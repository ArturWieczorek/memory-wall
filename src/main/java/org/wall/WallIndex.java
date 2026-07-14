package org.wall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * An in-memory cache of ALL wall posts (not just the recent page), so the feed can pin globally and
 * search the full history. It pages the provider newest-first and keeps every post it has seen,
 * keyed by transaction hash. Refresh is INCREMENTAL: it stops as soon as a page adds nothing new
 * (steady state) or the last page is reached (initial load), so ongoing cost is tiny.
 *
 * <p>Durability: the cache is backed by a {@link PostStore}. With the default {@link NoopPostStore}
 * it re-ingests from the chain on restart (fine at small scale); set {@code wall.index.db-path} to
 * back it with {@link SqlitePostStore}, and a restart starts warm (loads what is stored, then only
 * fetches what is new). Callers reorder for display, so map insertion order is not load-bearing.
 *
 * <p>Ingest (the provider paging) is integration - exercised against a live backend. The pure
 * accumulate/stop logic, and the seed-from / save-to-store wiring, are unit-tested with stubs
 * ({@code WallIndexTest}).
 */
@Component
public class WallIndex {

  private final BlockfrostFeedReader reader;
  private final PostStore store;
  private final int pageSize;
  private final int maxPages;

  // Guarded by refresh()'s lock.
  private final Map<String, WallPost> byTxHash = new LinkedHashMap<>();

  public WallIndex(
      BlockfrostFeedReader reader,
      PostStore store,
      @Value("${wall.index.page-size:100}") int pageSize,
      @Value("${wall.index.max-pages:500}") int maxPages) {
    this.reader = reader;
    this.store = store;
    this.pageSize = pageSize;
    this.maxPages = maxPages;
    // Warm start: seed from the store so a restart does not begin empty. Best-effort - a store
    // read error must not stop the app booting; the next refresh re-ingests from the chain anyway.
    try {
      for (WallPost post : store.loadAll()) {
        String tx = post.txHash();
        if (tx != null && !tx.isBlank()) {
          byTxHash.put(tx, post);
        }
      }
    } catch (RuntimeException e) {
      // start empty; the scheduled refresh will fill the cache from the chain
    }
  }

  /**
   * Refresh from the chain. Runs shortly after startup and then every {@code wall.index.refresh-ms}
   * (default 60s). Best-effort: a provider error is swallowed so a hiccup never crashes the app -
   * the cache simply keeps what it already has until the next tick.
   */
  @Scheduled(fixedDelayString = "${wall.index.refresh-ms:60000}", initialDelay = 2000)
  public void scheduledRefresh() {
    try {
      refresh();
    } catch (RuntimeException e) {
      // keep the existing cache; try again next tick
    }
  }

  /** Page newest-first, adding unseen posts; stop when caught up or the history ends. */
  public synchronized void refresh() {
    List<WallPost> newlyAdded = new ArrayList<>();
    for (int p = 1; p <= maxPages; p++) {
      List<WallPost> pagePosts = reader.pageRaw(pageSize, p);
      if (pagePosts.isEmpty()) {
        break; // no data / end of history
      }
      int added = 0;
      for (WallPost post : pagePosts) {
        String tx = post.txHash();
        if (tx != null && !tx.isBlank() && !byTxHash.containsKey(tx)) {
          byTxHash.put(tx, post);
          newlyAdded.add(post);
          added++;
        }
      }
      if (added == 0) {
        break; // this page was entirely already known -> caught up
      }
      if (pagePosts.size() < pageSize) {
        break; // a short page means we reached the oldest post
      }
    }
    // Persist only what is new (idempotent anyway); with the no-op store this does nothing.
    if (!newlyAdded.isEmpty()) {
      store.save(newlyAdded);
    }
  }

  /**
   * A snapshot of every post the index has seen (unordered/unmoderated; callers order + filter).
   */
  public synchronized List<WallPost> allPosts() {
    return new ArrayList<>(byTxHash.values());
  }
}
