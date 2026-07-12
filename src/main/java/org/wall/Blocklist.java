package org.wall;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Display-side moderation. A post whose author or message contains any configured
 * (case-insensitive) term is hidden from the feed. This does NOT remove anything from the
 * blockchain - the post is permanent on-chain; we simply choose not to show it in our feed. It is
 * the one moderation lever a chain-backed wall has, and the seam where you would later plug in a
 * report/takedown flow.
 */
@Component
public class Blocklist {

  private final List<String> terms;

  public Blocklist(WallProperties props) {
    this.terms =
        props.blocklist().stream().map(t -> t.toLowerCase()).filter(t -> !t.isBlank()).toList();
  }

  /** True if this post matches the blocklist and should be hidden from the feed. */
  public boolean isBlocked(WallPost post) {
    if (terms.isEmpty()) {
      return false;
    }
    String haystack = (post.author() + " " + post.message()).toLowerCase();
    return terms.stream().anyMatch(haystack::contains);
  }

  /** Return only the posts that are not blocked (keeping order). */
  public List<WallPost> filter(List<WallPost> posts) {
    if (terms.isEmpty()) {
      return posts;
    }
    return posts.stream().filter(p -> !isBlocked(p)).toList();
  }
}
