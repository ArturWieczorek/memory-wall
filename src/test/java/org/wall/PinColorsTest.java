package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pin colours (fixed palette)")
class PinColorsTest {

  @Test
  @DisplayName("accepts a palette colour (case-insensitive, trimmed); rejects anything else")
  void normalize() {
    assertThat(PinColors.normalize("mint")).isEqualTo("mint");
    assertThat(PinColors.normalize(" ROSE ")).isEqualTo("rose"); // trimmed + lowercased
    assertThat(PinColors.normalize("chartreuse")).isEmpty(); // not in the palette
    assertThat(PinColors.normalize("")).isEmpty();
    assertThat(PinColors.normalize(null)).isEmpty();
  }
}
