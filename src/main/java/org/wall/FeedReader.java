package org.wall;

import java.util.List;

/** Reads recent wall posts from the chain. An interface so it is easy to stub in tests. */
public interface FeedReader {

  /** The most recent posts, newest first (up to {@code limit}). */
  List<WallPost> recent(int limit);
}
