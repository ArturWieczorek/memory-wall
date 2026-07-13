package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoInputs;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Fee tip accounting (net received by the fee address)")
class BlockfrostFeedReaderTest {

  private static final String FEE = "addr_fee";
  private static final String OTHER = "addr_other";

  private static TxContentOutputAmount lovelace(long n) {
    return TxContentOutputAmount.builder().unit("lovelace").quantity(String.valueOf(n)).build();
  }

  private static TxContentUtxoInputs in(String addr, long n) {
    return TxContentUtxoInputs.builder().address(addr).amount(List.of(lovelace(n))).build();
  }

  private static TxContentUtxoOutputs out(String addr, long n) {
    return TxContentUtxoOutputs.builder().address(addr).amount(List.of(lovelace(n))).build();
  }

  @Test
  @DisplayName("a real tip from a different poster is counted")
  void realTip() {
    TxContentUtxo u =
        TxContentUtxo.builder()
            .inputs(List.of(in(OTHER, 10_000_000)))
            .outputs(List.of(out(FEE, 5_000_000), out(OTHER, 4_500_000)))
            .build();
    assertThat(BlockfrostFeedReader.netTipToFeeAddress(u, FEE)).isEqualTo(5_000_000);
  }

  @Test
  @DisplayName("a self-payment (fee address is also the sender) nets to zero - NOT a tip")
  void selfPaymentIsNotATip() {
    // The fee address funds the tx (input) and gets its change back (output): net is ~0, so the
    // poster's own change is never mistaken for a tip.
    TxContentUtxo u =
        TxContentUtxo.builder()
            .inputs(List.of(in(FEE, 3_318_780)))
            .outputs(List.of(out(FEE, 3_143_203)))
            .build();
    assertThat(BlockfrostFeedReader.netTipToFeeAddress(u, FEE)).isZero();
  }

  @Test
  @DisplayName("no fee address configured -> no tip")
  void noFeeAddress() {
    TxContentUtxo u =
        TxContentUtxo.builder()
            .inputs(List.of(in(OTHER, 10_000_000)))
            .outputs(List.of(out(FEE, 5_000_000)))
            .build();
    assertThat(BlockfrostFeedReader.netTipToFeeAddress(u, "")).isZero();
  }
}
