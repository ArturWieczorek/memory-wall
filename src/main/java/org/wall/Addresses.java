package org.wall;

import com.bloxbean.cardano.client.address.Address;

/** Small helpers for Cardano addresses. */
public final class Addresses {

  private Addresses() {}

  /**
   * Whether {@code bech32} parses as a Cardano address (correct Bech32, valid checksum). Used to
   * fail fast on a mis-typed operator fee address instead of letting every post's build blow up.
   */
  public static boolean isValid(String bech32) {
    if (bech32 == null || bech32.isBlank()) {
      return false;
    }
    try {
      new Address(bech32);
      return true;
    } catch (RuntimeException e) {
      return false; // bad checksum / not an address
    }
  }
}
