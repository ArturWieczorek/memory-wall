package org.wall;

import java.util.List;
import java.util.Set;

/**
 * The fixed palette a poster may tint a pinned post with. A curated set (not a free colour) keeps
 * the wall tasteful and is safe: only these known codes are ever stored or rendered, so an
 * arbitrary on-chain value (a CLI user could write anything into {@code c}) can never inject a
 * colour/CSS.
 */
public final class PinColors {

  private PinColors() {}

  /** Allowed colour codes, in display order. The UI maps each to a pastel; unknown -> default. */
  public static final List<String> PALETTE =
      List.of("rose", "mint", "sky", "lemon", "lilac", "peach");

  private static final Set<String> ALLOWED = Set.copyOf(PALETTE);

  /** Return the code if it is a known palette colour (case-insensitive), otherwise "" (default). */
  public static String normalize(String code) {
    if (code == null) {
      return "";
    }
    String c = code.trim().toLowerCase();
    return ALLOWED.contains(c) ? c : "";
  }
}
