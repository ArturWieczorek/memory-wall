package org.wall;

import java.util.Collection;
import java.util.List;

/**
 * Where the {@link WallIndex} keeps its copy of the wall so it survives a restart. Two
 * implementations:
 *
 * <ul>
 *   <li>{@link NoopPostStore} - the default: no persistence, the index re-ingests from the chain on
 *       every restart (fine for a small wall, zero dependencies).
 *   <li>{@link SqlitePostStore} - opt-in (set {@code wall.index.db-path}): a single SQLite file, so
 *       a restart starts warm and only fetches what is new.
 * </ul>
 *
 * <p>Both are best-effort from the index's point of view: a store error must never crash posting or
 * reading, it just means the next refresh re-does the work.
 */
public interface PostStore {

  /** Every post previously saved, newest-first. Empty if nothing is stored yet. */
  List<WallPost> loadAll();

  /**
   * Persist these posts. Idempotent: saving a post already stored is a no-op (keyed by tx hash).
   */
  void save(Collection<WallPost> posts);
}
