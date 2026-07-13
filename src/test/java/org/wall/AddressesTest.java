package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Addresses")
class AddressesTest {

  @Test
  @DisplayName("accepts a real address; rejects a malformed one, blank, or null")
  void isValid() {
    String realTestnet = new Account(Networks.testnet()).baseAddress(); // a genuine addr_test1...
    assertThat(Addresses.isValid(realTestnet)).isTrue();

    assertThat(Addresses.isValid("addr_test1qfeeaddr")).isFalse(); // right prefix, bad checksum
    assertThat(Addresses.isValid("not-an-address")).isFalse();
    assertThat(Addresses.isValid("")).isFalse();
    assertThat(Addresses.isValid(null)).isFalse();
  }
}
