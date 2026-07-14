package org.wall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** The wall feed: posts to show, newest first. */
public final class Feed {

  private Feed() {}

  /**
   * Order posts newest-first by timestamp. ISO-8601 instant strings sort lexicographically in
   * chronological order, so a plain string compare (reversed) is correct.
   */
  public static List<WallPost> newestFirst(List<WallPost> posts) {
    return posts.stream().sorted(Comparator.comparing(WallPost::timestamp).reversed()).toList();
  }

  /**
   * Display order: up to {@code maxPinned} ACTIVE pinned posts first (highest tip wins the scarce
   * slots), then everyone else newest-first. A post is pin-eligible if it paid the pin fee (see
   * {@link WallPost#pinned()}) AND is still within its pin window ({@code now - timestamp <
   * pinDurationSeconds}). Overflow (outbid) and expired pins are demoted to normal posts. {@code
   * maxPinned <= 0} means unlimited slots; {@code pinDurationSeconds <= 0} means pins never expire.
   * Orders only the posts passed in (the loaded window); pinning across the full history would need
   * an indexer.
   */
  public static List<WallPost> forDisplay(
      List<WallPost> posts, int maxPinned, Instant now, long pinDurationSeconds) {
    int cap = maxPinned <= 0 ? Integer.MAX_VALUE : maxPinned;
    // Active pins (paid + within window), highest tip first (ties broken newest-first).
    List<WallPost> eligible =
        posts.stream()
            .filter(p -> p.pinned() && pinActive(p.timestamp(), now, pinDurationSeconds))
            .sorted(
                Comparator.comparingLong(WallPost::tipLovelace)
                    .reversed()
                    .thenComparing(Comparator.comparing(WallPost::timestamp).reversed()))
            .toList();
    List<WallPost> pinnedTop = eligible.stream().limit(cap).toList();
    Set<WallPost> topSet = Collections.newSetFromMap(new IdentityHashMap<>());
    topSet.addAll(pinnedTop);
    // Everyone not in a top slot, newest-first; any still-flagged pin (expired or outbid) is
    // demoted.
    List<WallPost> rest =
        posts.stream()
            .filter(p -> !topSet.contains(p))
            .map(p -> p.pinned() ? demote(p) : p)
            .sorted(Comparator.comparing(WallPost::timestamp).reversed())
            .toList();
    List<WallPost> out = new ArrayList<>(pinnedTop);
    out.addAll(rest);
    return out;
  }

  /** Whether a pin is still within its window. Unparseable timestamps are treated as inactive. */
  private static boolean pinActive(String timestamp, Instant now, long pinDurationSeconds) {
    if (pinDurationSeconds <= 0) {
      return true; // pins never expire
    }
    try {
      return now.isBefore(Instant.parse(timestamp).plusSeconds(pinDurationSeconds));
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * Posts whose author or message contains {@code q} (case-insensitive). A blank query returns the
   * list unchanged. Pure - used for full-history search over the index.
   */
  public static List<WallPost> search(List<WallPost> posts, String q) {
    String needle = q == null ? "" : q.trim().toLowerCase();
    if (needle.isEmpty()) {
      return posts;
    }
    return posts.stream()
        .filter(p -> (p.author() + " " + p.message()).toLowerCase().contains(needle))
        .toList();
  }

  /**
   * One page (1-based) of at most {@code limit} items from an already-ordered list. Out-of-range
   * pages return empty. {@code limit <= 0} defaults to 20.
   */
  public static List<WallPost> page(List<WallPost> posts, int limit, int page) {
    int lim = limit <= 0 ? 20 : limit;
    int p = Math.max(1, page);
    int from = (p - 1) * lim;
    if (from >= posts.size()) {
      return List.of();
    }
    return List.copyOf(posts.subList(from, Math.min(from + lim, posts.size())));
  }

  /** A copy of {@code p} with {@code pinned=false} (missed a slot, or its window expired). */
  private static WallPost demote(WallPost p) {
    return new WallPost(
        p.author(),
        p.message(),
        p.timestamp(),
        p.txHash(),
        p.address(),
        p.tipLovelace(),
        false,
        p.color());
  }
}
