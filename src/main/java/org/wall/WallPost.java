package org.wall;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * One message on the wall: who posted, what they said, and when.
 *
 * @param author a short display name (max 64 bytes - the Cardano metadata string limit; may be
 *     empty)
 * @param message the message text (any length; chunked on-chain by {@link Wall})
 * @param timestamp ISO-8601 instant string, e.g. "2026-06-30T12:00:00Z"
 */
public record WallPost(String author, String message, String timestamp) {

  /** Cardano caps a single metadata text value at 64 bytes; the author must fit in one. */
  public static final int MAX_AUTHOR_BYTES = 64;

  public WallPost {
    author = author == null ? "" : author;
    if (author.getBytes(StandardCharsets.UTF_8).length > MAX_AUTHOR_BYTES) {
      throw new IllegalArgumentException("author must be at most " + MAX_AUTHOR_BYTES + " bytes");
    }
    if (message == null || message.isEmpty()) {
      throw new IllegalArgumentException("message must not be empty");
    }
    if (timestamp == null || timestamp.isBlank()) {
      throw new IllegalArgumentException("timestamp is required");
    }
  }

  /** A post stamped at {@code when} (supplied for testability). */
  public static WallPost create(String author, String message, Instant when) {
    return new WallPost(author, message, when.toString());
  }
}
