# Chapter 03 - The Backend API (Spring Boot)

> Goal: expose the wall over HTTP - an endpoint that builds an UNSIGNED post transaction (for the
> wallet to sign) and an endpoint that serves the feed. Booted and tested with MockMvc, no chain.

## 1. Why the backend builds, but does not sign

We want a real wallet experience without the server ever holding keys. The split:
- The **backend** builds the *unsigned* transaction (it knows how to assemble the post metadata and
  select the sender's UTxOs) and serves the feed.
- The **wallet** (browser, Chapter 04) signs and submits - it alone holds the keys.

This is the standard, safe dApp pattern: the server is a helpful clerk, never a key custodian.

## 2. What we build
- `WallProperties` + `WallConfig` - config (`wall.backend-url`) and the `BackendService` bean.
- `FeedReader` (interface) + `BlockfrostFeedReader` - query the chain for our metadata label and parse
  each transaction's JSON into a `WallPost`, newest-first.
- `PostTxBuilder` - build the unsigned post tx (a 1-ADA self-payment carrying the metadata) and return
  its CBOR via `Transaction.serializeToHex()`.
- `WallController` - `GET /api/feed` and `POST /api/posts/build`.

## 3. Tests we write first (TDD)
With MockMvc and a **stubbed `FeedReader`** (so no chain is needed):
- `GET /api/feed` returns the stubbed posts as JSON.
- `POST /api/posts/build` with an empty message returns **400** (validation happens before any chain
  work).
The full context loads, proving every bean wires up.

## 4. Steps
- Add Spring Boot (web + test starters) to the build; `WallApplication` is the entry point.
- Program the controller against the `FeedReader` interface; the real `BlockfrostFeedReader` is a
  bean, and the test swaps in a mock with `@MockitoBean`.
- `PostTxBuilder` uses QuickTx `.build()` (not `.buildAndSign()`) to get an **unsigned** transaction.

## 5. What to notice / common mistakes
- **`.build()` vs `.buildAndSign()`.** We deliberately do not sign on the server - `.build()` returns
  the unsigned tx; signing is the wallet's job.
- **Validate before building.** Cheap checks (empty message, missing address) return 400 without
  touching the chain - keeps bad requests fast and the unit test offline.
- **The feed query is integration.** `BlockfrostFeedReader` needs a live backend; we unit-test the
  controller with a stub and exercise the real reader on a devnet/testnet.
- Submitting the signed tx (assembling the wallet's witness) is added in Chapter 04 with the UI.

## 6. Build and commit
```bash
./gradlew spotlessApply test
git add -A && git commit -m "feat(ch03): Spring backend - build unsigned post tx + serve the feed"
git tag ch03
```

## 7. What is next
Chapter 04 builds the Next.js web UI: connect a CIP-30 wallet, post (backend builds, wallet signs,
backend submits), and render the live feed.

## Glossary (Chapter 03)
- **Unsigned transaction** - a built tx with no signatures yet; the wallet adds them.
- **`FeedReader`** - the interface for reading posts (real Blockfrost impl + stub for tests).
- **`@MockitoBean`** - Spring Boot's way to replace a bean with a mock in a test.
- **`serializeToHex`** - the tx's CBOR as hex, handed to the wallet to sign.
