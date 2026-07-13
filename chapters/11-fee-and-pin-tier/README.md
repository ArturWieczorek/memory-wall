# Chapter 11 - Fee and pin tier (tip to post, tip more to pin)

> Goal: an OPTIONAL economy for the wall. When the operator turns it on, posting costs a small **tip**
> to a fee address, and tipping more **pins** your post to the top. Pins are **scarce** (a few slots),
> **competitive** (highest tips win), **time-limited** (they expire), and **verified on-chain** (we
> read the actual amount paid, so pinning cannot be faked).
>
> Written for a beginner - each idea gets a plain-language analogy.

---

## 1. What we are (and are not) building

A "pay to post / pay more to pin" tier. But two facts about this wall shape it honestly:

- **The wall is permissionless.** Anyone can post by writing label-1719 metadata themselves (via
  `cardano-cli`), bypassing our backend. So we cannot *force* a fee on everyone - a determined user
  can post free. What we *can* do: require a tip for posts built **through our UI**, and rank/pin by
  the **actual on-chain payment** so nobody can fake a pin. Think of it as a tip jar with a VIP shelf,
  not a paywall.
- **The backend is stateless** (no database - it reads the chain per request). So pinning is derived
  entirely from the chain: "did this transaction pay the fee address, and how much?" No server state,
  no queue.

The whole tier is **off by default** (no fee address configured = free posting, exactly as before).

## 2. The rules (the UI states these to users)

When the operator sets a fee address:
- **Post:** tip at least `min-fee` (e.g. 2 ADA) to the fee address.
- **Pin:** tip at least `pin-fee` (e.g. 5 ADA) to pin your post to the top.
- **Scarcity:** at most `max-pinned` posts (default 3) are pinned at once.
- **Competition:** if more posts paid to pin than there are slots, the **highest tips** hold them; a
  bigger tip can **bump** a smaller one out (back into the normal feed).
- **Expiry:** a pin lasts at most `pin-duration` (default 7 days) from its timestamp, then reverts to
  a normal post.

*Analogy:* a few billboard slots auctioned continuously - highest bidders are up, you can be outbid,
and every booking has a maximum run time.

Because users are spending real (test) ADA, the UI shows these rules in plain language next to the
tip field, using the real numbers from `GET /api/config`.

## 3. How it works

- **Config** (`WallProperties`): `feeAddress`, `minFeeLovelace`, `pinFeeLovelace`, `maxPinned`,
  `pinDurationSeconds`. `feeEnabled()` is just "is a fee address set?".
- **Building** (`PostTxBuilder`): when the tier is on, the built transaction gets an extra output
  paying the tip to the fee address (on top of the tiny self-payment that carries the metadata).
  `POST /api/posts/build` rejects a tip below `min-fee`.
- **Reading the tip back** (`BlockfrostFeedReader`): we already fetch each post's transaction UTxOs
  (Chapter 08, for the payer address) - the **same lookup** also sums the lovelace paid to the fee
  address. That verified amount is the post's tip; a post is pin-eligible if it reached `pin-fee`.
- **Ordering** (`Feed.forDisplay`): pure and unit-tested. Among eligible pins **still within their
  window**, the highest tips take the (capped) slots; overflow and expired pins are **demoted** to
  normal posts; everyone else is newest-first. Now is passed in, so the logic is deterministic to
  test.
- **UI:** reads `/api/config`; if the tier is on, shows a tip field (prefilled to the minimum), a
  live "will pin" hint, the rules text, and blocks a below-minimum tip. Pinned posts render with a
  pastel highlight and a `PINNED - N ADA` badge.

## 4. Tests first
- `Feed.forDisplay` (pure): pins first by tip; cap demotes the outbid pin; an out-of-window pin
  expires to normal (`FeedAndParseTest`).
- `WallProperties` defaults; `WallPost` carries tip + pinned (`WallPostTest`).
- API: `/api/config` reports the tier (off by default; on with thresholds in `WallFeeApiTest`); a
  below-minimum tip is rejected; the feed JSON includes `tipLovelace` + `pinned` (`WallApiTest`).
- The actual tx-building and on-chain read are integration (need a live provider), like the rest of
  `PostTxBuilder`/`BlockfrostFeedReader`.
- UI: `lovelaceToAda`/`adaToLovelace` (pure), and `FeedList` renders the PINNED badge + tip.

## 5. Honest boundaries
- **Permissionless bypass:** a CLI user can pay the fee address (or not) without our UI; the feed
  still ranks everyone fairly by what they actually paid, but we can't *require* a fee globally.
- **No guaranteed tenure:** you buy a competitive slot for up to the window - a bigger tipper can bump
  you sooner. (A guaranteed-duration queue would need a stateful backend + a database; we chose the
  stateless auction on purpose.)
- **Pinning orders the loaded window,** not all history - global pinning across every post ever would
  need an indexer (backlog).
- **Colour:** pinned posts get one default pastel here; letting the payer pick a palette colour
  (stored on-chain) is the next chapter.

## 6. Running it
```bash
./gradlew spotlessApply test         # backend: 37 tests

# try the tier locally (fake address is fine - the build is rejected before it hits the chain):
WALL_FEE_ADDRESS=addr_test1qexample WALL_MIN_FEE_LOVELACE=2000000 \
WALL_PIN_FEE_LOVELACE=5000000 ./infra/run-backend.sh
curl localhost:8090/api/config       # feeEnabled true + thresholds

cd ui && npm test                    # UI: 29 tests
npm run typecheck && npm run build
```

## 7. What to notice / common mistakes
- **Verify, do not trust.** Read the tip from the transaction's outputs, never from a metadata field
  a poster could set (same lesson as the author name in Chapter 08).
- **Pass "now" in.** Expiry logic that calls the clock internally is hard to test - inject it.
- **Scarcity needs a cap AND an order.** Without a limit, "pay to pin" floods the top; without an
  order, ties are arbitrary. We cap by `max-pinned` and rank by tip.
- **State the rules in the UI.** Users spend real value - they must see the cost, the pin threshold,
  the slot count, the duration, and that they can be outbid, before they pay.
- **Keep it off by default.** A free wall stays free until the operator opts in.

## Glossary (Chapter 11)
- **Lovelace / ADA** - 1 ADA = 1,000,000 lovelace (amounts on Cardano are in lovelace).
- **Fee address** - the operator's address that tips are paid to.
- **Tip** - lovelace a post pays to the fee address (verified on-chain).
- **Pin** - a top-of-feed slot won by tipping at least the pin fee; scarce, competitive, and expiring.
- **Eviction / bump** - losing a pin slot to a higher tip before your window ends.
- **Pin window** - the maximum time a pin lasts before reverting to a normal post.
- **Stateless** - the backend keeps no database; pinning is recomputed from the chain each request.
