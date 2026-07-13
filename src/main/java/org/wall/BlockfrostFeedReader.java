package org.wall;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoInputs;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads the feed by querying the backend for all transactions carrying our metadata label, parsing
 * each one's JSON metadata into a {@link WallPost}, and enriching it from the transaction's UTxOs
 * (the verified payer address and any tip paid to the operator's fee address). (Integration: needs
 * a live backend; the controller is unit-tested with a stubbed {@link FeedReader} instead.)
 */
@Component
public class BlockfrostFeedReader implements FeedReader {

  private final BackendService backend;
  private final WallProperties props;

  public BlockfrostFeedReader(BackendService backend, WallProperties props) {
    this.backend = backend;
    this.props = props;
  }

  @Override
  public List<WallPost> recent(int limit, int page) {
    int p = Math.max(1, page);
    try {
      Result<List<MetadataJSONContent>> result =
          backend
              .getMetadataService()
              .getJSONMetadataByLabel(
                  BigInteger.valueOf(Wall.WALL_LABEL), limit, p, OrderEnum.desc);
      if (!result.isSuccessful() || result.getValue() == null) {
        return List.of();
      }
      List<WallPost> posts = new ArrayList<>();
      for (MetadataJSONContent content : result.getValue()) {
        WallPost post = tryParse(content.getJsonMetadata(), content.getTxHash());
        if (post != null) {
          posts.add(enrich(post, content.getTxHash()));
        }
      }
      return Feed.forDisplay(posts, props.maxPinned(), Instant.now(), props.pinDurationSeconds());
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
      String color = PinColors.normalize(node.path("c").asText(""));
      return new WallPost(
          author, message.toString(), ts, txHash == null ? "" : txHash, "", 0L, false, color);
    } catch (RuntimeException e) {
      return null; // skip anything that does not look like one of our posts
    }
  }

  /**
   * Fill in the verified payer address and the tip paid to the fee address from the transaction's
   * UTxOs (one lookup per post). Best-effort: on any failure the post keeps its defaults (no
   * address, no tip), so a provider hiccup never breaks the feed. A post is "pinned" only if its
   * tip actually reached the pin threshold on-chain.
   */
  private WallPost enrich(WallPost post, String txHash) {
    String address = "";
    long tip = 0;
    try {
      Result<TxContentUtxo> res = backend.getTransactionService().getTransactionUtxos(txHash);
      if (res.isSuccessful() && res.getValue() != null) {
        address = firstInputAddress(res.getValue());
        tip = netTipToFeeAddress(res.getValue(), props.feeAddress());
      }
    } catch (ApiException e) {
      // best-effort enrichment; leave defaults
    }
    boolean pinned =
        props.feeEnabled() && props.pinFeeLovelace() > 0 && tip >= props.pinFeeLovelace();
    return new WallPost(
        post.author(),
        post.message(),
        post.timestamp(),
        txHash,
        address,
        tip,
        pinned,
        post.color());
  }

  /** The first input's address - who funded (and therefore signed) the transaction. */
  private static String firstInputAddress(TxContentUtxo utxo) {
    if (utxo.getInputs() != null) {
      for (TxContentUtxoInputs in : utxo.getInputs()) {
        if (in.getAddress() != null && !in.getAddress().isBlank()) {
          return in.getAddress();
        }
      }
    }
    return "";
  }

  /**
   * NET lovelace the fee address actually received = lovelace paid TO it (outputs) minus lovelace
   * it contributed (inputs). This is ~0 for a self-payment (an operator whose fee address is also
   * the posting wallet - their change returns and nets out, so it is NOT counted as a tip) and the
   * real amount when a different poster pays. Clamped at 0. Static + package-private for unit
   * testing.
   */
  static long netTipToFeeAddress(TxContentUtxo utxo, String feeAddress) {
    if (feeAddress == null || feeAddress.isBlank() || utxo == null) {
      return 0;
    }
    long out = 0;
    if (utxo.getOutputs() != null) {
      for (TxContentUtxoOutputs o : utxo.getOutputs()) {
        if (feeAddress.equals(o.getAddress())) {
          out += lovelaceOf(o.getAmount());
        }
      }
    }
    long in = 0;
    if (utxo.getInputs() != null) {
      for (TxContentUtxoInputs i : utxo.getInputs()) {
        if (feeAddress.equals(i.getAddress())) {
          in += lovelaceOf(i.getAmount());
        }
      }
    }
    return Math.max(0, out - in);
  }

  /** Sum the lovelace across a list of amounts (ignores native tokens / bad quantities). */
  private static long lovelaceOf(List<TxContentOutputAmount> amounts) {
    if (amounts == null) {
      return 0;
    }
    long sum = 0;
    for (TxContentOutputAmount a : amounts) {
      if ("lovelace".equals(a.getUnit())) {
        try {
          sum += Long.parseLong(a.getQuantity());
        } catch (NumberFormatException ignore) {
          // skip a non-numeric quantity
        }
      }
    }
    return sum;
  }
}
