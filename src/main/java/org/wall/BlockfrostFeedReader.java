package org.wall;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoInputs;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads the feed by querying the backend for all transactions carrying our metadata label, and
 * parsing each one's JSON metadata into a {@link WallPost}. (Integration: needs a live backend; the
 * controller is unit-tested with a stubbed {@link FeedReader} instead.)
 */
@Component
public class BlockfrostFeedReader implements FeedReader {

  private final BackendService backend;

  public BlockfrostFeedReader(BackendService backend) {
    this.backend = backend;
  }

  @Override
  public List<WallPost> recent(int limit) {
    try {
      Result<List<MetadataJSONContent>> result =
          backend
              .getMetadataService()
              .getJSONMetadataByLabel(
                  BigInteger.valueOf(Wall.WALL_LABEL), limit, 1, OrderEnum.desc);
      if (!result.isSuccessful() || result.getValue() == null) {
        return List.of();
      }
      List<WallPost> posts = new ArrayList<>();
      for (MetadataJSONContent content : result.getValue()) {
        WallPost post = tryParse(content.getJsonMetadata(), content.getTxHash());
        if (post != null) {
          // Best-effort verified identity: the address that signed this tx (one lookup per post).
          // Unlike the free-text author, it cannot be spoofed.
          String address = payerAddress(content.getTxHash());
          posts.add(
              new WallPost(
                  post.author(), post.message(), post.timestamp(), post.txHash(), address));
        }
      }
      return Feed.newestFirst(posts);
    } catch (ApiException e) {
      throw new IllegalStateException("feed query failed", e);
    }
  }

  /**
   * Parse one post from its JSON metadata + transaction hash, returning null if it is malformed.
   */
  private static WallPost tryParse(JsonNode node, String txHash) {
    if (node == null) {
      return null;
    }
    try {
      String author = node.path("a").asText("");
      StringBuilder message = new StringBuilder();
      JsonNode m = node.path("m");
      if (m.isArray()) {
        m.forEach(chunk -> message.append(chunk.asText()));
      } else {
        message.append(m.asText(""));
      }
      String ts = node.path("ts").asText("");
      if (message.isEmpty() || ts.isBlank()) {
        return null;
      }
      return new WallPost(author, message.toString(), ts, txHash == null ? "" : txHash);
    } catch (RuntimeException e) {
      return null; // skip anything that does not look like one of our posts
    }
  }

  /**
   * The address that funded (and therefore signed) the transaction - read from its first input.
   * Best-effort: returns "" if the lookup fails, so a provider hiccup never breaks the feed. For a
   * simple self-payment post the inputs all belong to the poster's wallet.
   */
  private String payerAddress(String txHash) {
    try {
      Result<TxContentUtxo> utxos = backend.getTransactionService().getTransactionUtxos(txHash);
      if (utxos.isSuccessful()
          && utxos.getValue() != null
          && utxos.getValue().getInputs() != null) {
        for (TxContentUtxoInputs in : utxos.getValue().getInputs()) {
          if (in.getAddress() != null && !in.getAddress().isBlank()) {
            return in.getAddress();
          }
        }
      }
    } catch (ApiException e) {
      // best-effort enrichment; leave the address empty on failure
    }
    return "";
  }
}
