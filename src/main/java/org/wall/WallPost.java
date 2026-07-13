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
 * @param txHash the on-chain transaction hash (empty until known, e.g. while building a post)
 * @param address the verified payer address that signed the transaction (empty if not resolved);
 *     the free-text {@code author} is a claim, whereas this is read from the chain and cannot be
 *     faked
 * @param tipLovelace lovelace paid to the operator's fee address by this post (read from the chain;
 *     0 if none or the fee tier is off)
 * @param pinned whether the tip reached the pin threshold, so the post is shown first (verified
 *     from the on-chain payment, not self-declared)
 * @param color optional pin-colour code from the fixed palette ({@link PinColors}); empty =
 *     default. Cosmetic and only applied when the post is pinned
 */
public record WallPost(
    String author,
    String message,
    String timestamp,
    String txHash,
    String address,
    long tipLovelace,
    boolean pinned,
    String color) {

  /** Cardano caps a single metadata text value at 64 bytes; the author must fit in one. */
  public static final int MAX_AUTHOR_BYTES = 64;

  public WallPost {
    author = author == null ? "" : author;
    txHash = txHash == null ? "" : txHash;
    address = address == null ? "" : address;
    color = color == null ? "" : color;
    if (tipLovelace < 0) {
      tipLovelace = 0;
    }
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

  /** A post without a known transaction hash or address yet (e.g. while building, or in tests). */
  public WallPost(String author, String message, String timestamp) {
    this(author, message, timestamp, "", "", 0L, false, "");
  }

  /** A post with a tx hash but no resolved payer address. */
  public WallPost(String author, String message, String timestamp, String txHash) {
    this(author, message, timestamp, txHash, "", 0L, false, "");
  }

  /** A post with a tx hash and payer address but no tip/pin info (e.g. the read fallback). */
  public WallPost(String author, String message, String timestamp, String txHash, String address) {
    this(author, message, timestamp, txHash, address, 0L, false, "");
  }

  /** A post with tip/pin info but no chosen colour. */
  public WallPost(
      String author,
      String message,
      String timestamp,
      String txHash,
      String address,
      long tipLovelace,
      boolean pinned) {
    this(author, message, timestamp, txHash, address, tipLovelace, pinned, "");
  }

  /** A post stamped at {@code when} (supplied for testability). */
  public static WallPost create(String author, String message, Instant when) {
    return new WallPost(author, message, when.toString());
  }
}
