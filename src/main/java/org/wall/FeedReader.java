package org.wall;

import java.util.List;

/** Reads recent wall posts from the chain. An interface so it is easy to stub in tests. */
public interface FeedReader {

  /** One page of posts (up to {@code limit}) for a 1-based {@code page}, newest/pinned first. */
  List<WallPost> recent(int limit, int page);

  /** The first page (convenience). */
  default List<WallPost> recent(int limit) {
    return recent(limit, 1);
  }
}
