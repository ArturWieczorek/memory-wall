package org.wall;

import java.util.Collection;
import java.util.List;

/**
 * The default store: it stores nothing. With it, the {@link WallIndex} lives purely in memory and
 * re-ingests the wall from the chain on every restart. Zero dependencies, no files, no cleanup -
 * the right choice until the wall is large enough that a cold re-ingest is noticeable.
 */
public class NoopPostStore implements PostStore {

  @Override
  public List<WallPost> loadAll() {
    return List.of();
  }

  @Override
  public void save(Collection<WallPost> posts) {
    // intentionally nothing
  }
}
