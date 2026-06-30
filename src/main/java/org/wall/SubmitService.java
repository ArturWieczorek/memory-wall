package org.wall;

import co.nstant.in.cbor.CborDecoder;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.HexUtil;
import org.springframework.stereotype.Component;

/**
 * Submits a post transaction once the browser wallet has signed it. The wallet's CIP-30 {@code
 * signTx} returns a witness set (the signature); since the transaction we built is otherwise
 * unsigned and only the wallet signs it, we simply attach that witness set and submit.
 *
 * <p>(Integration: needs a live backend; exercised on a devnet/testnet, not in unit tests.)
 */
@Component
public class SubmitService {

  private final BackendService backend;

  public SubmitService(BackendService backend) {
    this.backend = backend;
  }

  /** Attach the wallet's witness to the unsigned tx and submit; returns the transaction hash. */
  public String submit(String unsignedTxCborHex, String witnessSetCborHex) {
    try {
      Transaction tx = Transaction.deserialize(HexUtil.decodeHexString(unsignedTxCborHex));
      co.nstant.in.cbor.model.Map witnessMap =
          (co.nstant.in.cbor.model.Map)
              CborDecoder.decode(HexUtil.decodeHexString(witnessSetCborHex)).get(0);
      TransactionWitnessSet walletWitnesses = TransactionWitnessSet.deserialize(witnessMap);
      tx.setWitnessSet(walletWitnesses); // the wallet is the only signer of this tx

      Result<String> result = backend.getTransactionService().submitTransaction(tx.serialize());
      if (!result.isSuccessful()) {
        throw new IllegalStateException("submit failed: " + result.getValue());
      }
      return result.getValue();
    } catch (Exception e) {
      throw new IllegalStateException("failed to submit signed transaction", e);
    }
  }
}
