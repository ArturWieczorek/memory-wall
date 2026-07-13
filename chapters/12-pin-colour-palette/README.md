# Chapter 12 - Let the payer colour their pin

> Goal: when someone pays to pin a post, let them tint it with a colour they pick from a small
> **palette**. The choice is stored **on-chain** (so it travels with the post) and rendered as a soft
> pastel behind their pinned post. Kept to a curated palette - tasteful and safe.
>
> Written for a beginner - short, because it builds on Chapter 11's pinning.

---

## 1. Why a fixed palette (not a free colour picker)

A free "any colour" picker seems friendlier, but it has two problems: it gets **garish** (neon
clashing walls), and it means putting an **arbitrary user string** into a style - a minor injection
risk, and definitely a taste risk. So we offer a **fixed palette** of six pastels (rose, mint, sky,
lemon, lilac, peach). Only those codes are ever accepted or rendered.

*Analogy:* a set of preset highlighter colours, not a paint-mixing machine.

## 2. The colour is on-chain (and self-declared - which is fine here)

The colour is stored in the post's metadata as an optional field **`c`** (next to `a`/`m`/`ts`). Note
this is **self-declared** - unlike the tip (verified from the payment) or the address (verified from
the signature), a poster simply writes their colour choice. That is completely fine, because a colour
is **cosmetic** - there is nothing to cheat. The only risk is a junk value, so we defend by
**normalising against the palette in both directions**:

- **On write** (`POST /api/posts/build`): `PinColors.normalize(...)` keeps the value only if it is a
  palette code, else stores nothing.
- **On read** (the feed reader, and the browser read-fallback): we normalise again, so even a colour
  someone wrote directly with `cardano-cli` (`c: "neon-green"`) is ignored and falls back to the
  default pastel.

*Lesson:* validate untrusted data at the boundary you control - and for on-chain data you must
re-validate on read, because anyone can write anything on-chain.

## 3. What we built

- **Backend:** `PinColors` (the palette + `normalize`). `WallPost` gains an optional `color`;
  `Wall.postMap`/`parsePost` write/read `c`; the feed reader parses + normalises it and carries it
  through enrichment. `/api/config` now returns the `palette` so the UI shows the right swatches.
  `POST /api/posts/build` accepts an optional `color`.
- **UI:** when your tip will pin (Chapter 11), a row of colour **swatches** appears; your pick is sent
  with the post. In the feed, a pinned post's background is its colour's pastel (default pastel if
  none). Colours are theme-aware (a lighter pastel in light mode, a deep muted one in dark).

## 4. Tests
- `PinColors.normalize` accepts palette codes (case-insensitive), rejects everything else
  (`PinColorsTest`).
- `WallPost` carries the colour; it round-trips through metadata (`WallPostTest`, `FeedAndParseTest`).
- `/api/config` includes the palette; the feed JSON includes `color` (`WallApiTest`).
- UI: `pinColorBg` maps a palette code to its CSS var (else the default); `rowToPost` keeps a palette
  colour and drops a non-palette one (`lib.test.ts`).

## 5. Running it
```bash
./gradlew spotlessApply test     # backend: 40 tests
cd ui && npm test                # UI: 31 tests
npm run typecheck && npm run build
# with the fee tier on (Chapter 11), tip enough to pin -> the colour swatches appear.
```

## 6. What to notice / common mistakes
- **Re-validate on-chain data on read.** Writing safely is not enough; anyone can put anything
  on-chain, so normalise again when you render.
- **Curate cosmetic choices.** A fixed palette beats a free picker for taste and safety.
- **Cosmetic vs verified.** The colour is self-declared (fine - it is decoration); the tip and address
  stay verified. Know which of your fields need proof and which do not.

## Glossary (Chapter 12)
- **Palette** - the fixed set of allowed colours.
- **`c` (metadata field)** - the optional on-chain colour code on a post.
- **Normalise** - reduce input to a known-good value (a palette code) or a safe default ("").
- **Self-declared vs verified** - data a user simply asserts, versus data the system checks against
  the chain.
