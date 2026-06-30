package org.wall;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
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

  /**
   * Build the unsigned post transaction for {@code senderAddress}; returns its CBOR hex. The
   * address may be bech32 ({@code addr...}) or the hex form a CIP-30 wallet returns - we normalise
   * it.
   */
  public String buildUnsignedHex(String senderAddress, WallPost post) {
    String bech32 = toBech32(senderAddress);
    Tx tx =
        new Tx()
            .payToAddress(bech32, Amount.ada(1))
            .attachMetadata(Wall.postMetadata(post))
            .from(bech32);
    try {
      Transaction unsigned = new QuickTxBuilder(backend).compose(tx).build();
      return unsigned.serializeToHex();
    } catch (Exception e) {
      throw new IllegalStateException("failed to build post transaction", e);
    }
  }

  /** A CIP-30 wallet returns addresses as hex; convert to the bech32 form bloxbean expects. */
  private static String toBech32(String address) {
    if (address.startsWith("addr")) {
      return address;
    }
    return new Address(HexUtil.decodeHexString(address)).toBech32();
  }
}
