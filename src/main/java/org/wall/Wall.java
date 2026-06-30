package org.wall;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link WallPost} into Cardano transaction metadata - the simplest on-chain storage (no
 * smart contract needed). A post is recorded under a fixed label as {@code {a, m, ts}}, where the
 * message {@code m} is a LIST of <=64-byte chunks (because a single metadata text value is capped
 * at 64 bytes).
 */
public final class Wall {

  private Wall() {}

  /** Our metadata label (namespaces wall posts on-chain). */
  public static final long WALL_LABEL = 1719;

  /** The maximum bytes in one metadata text value. */
  public static final int MAX_CHUNK_BYTES = 64;

  /**
   * Split a message into <=64-byte UTF-8 chunks, never splitting a character across a chunk
   * boundary. Reassembling the chunks in order yields the original message.
   */
  public static List<String> chunk(String message) {
    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int currentBytes = 0;
    int i = 0;
    while (i < message.length()) {
      int codePoint = message.codePointAt(i);
      String ch = new String(Character.toChars(codePoint));
      int chBytes = ch.getBytes(StandardCharsets.UTF_8).length;
      if (currentBytes + chBytes > MAX_CHUNK_BYTES) {
        chunks.add(current.toString());
        current.setLength(0);
        currentBytes = 0;
      }
      current.append(ch);
      currentBytes += chBytes;
      i += Character.charCount(codePoint);
    }
    if (current.length() > 0 || chunks.isEmpty()) {
      chunks.add(current.toString());
    }
    return chunks;
  }

  /** The metadata map for a post: {@code {a: author, m: [chunks], ts: timestamp}}. */
  public static CBORMetadataMap postMap(WallPost post) {
    CBORMetadataList messageChunks = new CBORMetadataList();
    for (String chunk : chunk(post.message())) {
      messageChunks.add(chunk);
    }
    CBORMetadataMap map = new CBORMetadataMap();
    map.put("a", post.author());
    map.put("m", messageChunks);
    map.put("ts", post.timestamp());
    return map;
  }

  /** The full transaction metadata for a post, under {@link #WALL_LABEL}. */
  public static Metadata postMetadata(WallPost post) {
    CBORMetadata metadata = new CBORMetadata();
    metadata.put(BigInteger.valueOf(WALL_LABEL), postMap(post));
    return metadata;
  }
}
