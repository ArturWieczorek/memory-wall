package org.wall;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Display-side moderation. A post is hidden from the feed if its author or message contains any
 * configured (case-insensitive) term, OR its transaction hash is on the blocked list. This does NOT
 * remove anything from the blockchain - the post is permanent on-chain; we simply choose not to
 * show it in our feed. It is the one moderation lever a chain-backed wall has, and the seam where
 * you would later plug in a report/takedown flow. Terms are a broad brush (hide anything mentioning
 * X); blocked tx hashes are a scalpel (hide exactly this one post).
 */
@Component
public class Blocklist {

  private final List<String> terms;
  private final Set<String> blockedTxHashes;

  public Blocklist(WallProperties props) {
    this.terms =
        props.blocklist().stream().map(t -> t.toLowerCase()).filter(t -> !t.isBlank()).toList();
    this.blockedTxHashes =
        props.blockedTxHashes().stream()
            .map(h -> h.toLowerCase().strip())
            .filter(h -> !h.isBlank())
            .collect(Collectors.toUnmodifiableSet());
  }

  /** True if this post matches the blocklist and should be hidden from the feed. */
  public boolean isBlocked(WallPost post) {
    if (terms.isEmpty() && blockedTxHashes.isEmpty()) {
      return false;
    }
    if (!post.txHash().isBlank() && blockedTxHashes.contains(post.txHash().toLowerCase().strip())) {
      return true;
    }
    String haystack = (post.author() + " " + post.message()).toLowerCase();
    return terms.stream().anyMatch(haystack::contains);
  }

  /** Return only the posts that are not blocked (keeping order). */
  public List<WallPost> filter(List<WallPost> posts) {
    if (terms.isEmpty() && blockedTxHashes.isEmpty()) {
      return posts;
    }
    return posts.stream().filter(p -> !isBlocked(p)).toList();
  }
}
