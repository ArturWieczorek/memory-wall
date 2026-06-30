package org.wall;

import java.util.Comparator;
import java.util.List;

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
}
