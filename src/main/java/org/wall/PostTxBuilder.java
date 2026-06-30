package org.wall;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.springframework.stereotype.Component;

/**
 * Builds the UNSIGNED transaction that records a post: a tiny self-payment carrying the post
 * metadata, paid for by the sender's own UTxOs. We return the unsigned CBOR; the browser wallet
 * signs and submits it (the server never holds keys). (Integration: building selects the sender's
 * UTxOs from the backend.)
 */
@Component
public class PostTxBuilder {

  private final BackendService backend;

  public PostTxBuilder(BackendService backend) {
    this.backend = backend;
  }

  /** Build the unsigned post transaction for {@code senderAddress}; returns its CBOR hex. */
  public String buildUnsignedHex(String senderAddress, WallPost post) {
    Tx tx =
        new Tx()
            .payToAddress(senderAddress, Amount.ada(1))
            .attachMetadata(Wall.postMetadata(post))
            .from(senderAddress);
    try {
      Transaction unsigned = new QuickTxBuilder(backend).compose(tx).build();
      return unsigned.serializeToHex();
    } catch (Exception e) {
      throw new IllegalStateException("failed to build post transaction", e);
    }
  }
}
