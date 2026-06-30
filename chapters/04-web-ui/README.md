# Chapter 04 - The Web UI (Next.js + CIP-30 wallet)

> Goal: the actual wall page. A Next.js front end connects a browser wallet, posts a message
> (backend builds, wallet signs, backend submits), and renders the live feed. The UI typechecks
> here; the wallet step itself needs a real browser + wallet.

Files: `ui/` (Next.js app), plus the backend's submit endpoint (`SubmitService`, `/api/posts/submit`).

## 1. The full posting flow

A post is a three-step handshake that keeps your keys in your wallet:
```
1. UI  -> POST /api/posts/build {address, author, message}  -> { txCbor }   (backend builds, unsigned)
2. UI  -> wallet.signTx(txCbor, true)                        -> witness      (wallet signs, in browser)
3. UI  -> POST /api/posts/submit {txCbor, witness}           -> { txHash }   (backend attaches + submits)
```
The server builds and submits but never signs; the wallet signs but never builds. That is the safe,
standard dApp split.

## 2. The submit endpoint (`SubmitService`)

The wallet's `signTx` returns just a **witness set** (the signature), not a full transaction. Since
our built transaction is otherwise unsigned and the wallet is its only signer, the backend simply
**attaches that witness set to the transaction and submits it** - no fiddly witness-merging needed.
(`/api/posts/submit`.) The address a CIP-30 wallet hands back is hex, so the backend normalises it to
bech32 before building.

## 3. The UI (`ui/`)

A minimal Next.js + TypeScript page (`app/page.tsx`):
- lists installed CIP-30 wallets from `window.cardano`, lets you connect one,
- a form posts via the three-step flow above,
- and it renders the feed from `GET /api/feed`.

Crucially, the UI uses **no Cardano JavaScript library** - it only calls the wallet's CIP-30 methods
(`enable`, `getChangeAddress`, `signTx`) and our backend. The backend does all the transaction work,
so the front end stays tiny. `next.config.mjs` proxies `/api/*` to the Java backend so the browser
calls it same-origin.

## 4. Running it
```bash
# terminal 1: the backend (point it at a devnet/preprod backend via WALL_BACKEND_URL)
./gradlew run

# terminal 2: the UI
cd ui && npm install && npm run dev      # http://localhost:3000  (proxies /api to :8090)
```
Open the page in a browser with a CIP-30 wallet (Lace/Eternl) set to the same network, connect, and
post. Verify it here without a wallet:
```bash
cd ui && npm install && npm run typecheck   # TypeScript compiles clean
./gradlew test                              # backend wiring + endpoints
```

## 5. What to notice / common mistakes
- **`signTx(tx, true)` - the `true` is partial-sign.** The wallet returns only its witness; you must
  combine it with the transaction before submitting (we do that on the backend).
- **No JS Cardano lib needed** because the backend builds and assembles. If you ever build the tx in
  the browser instead, you would need Lucid/Mesh - a much heavier front end.
- **Address format:** CIP-30 returns hex addresses; the backend converts to bech32. A common beginner
  bug is sending the hex straight into a builder that expects bech32.
- The wallet interaction cannot be tested headless; we typecheck the UI and unit-test the backend,
  and the live click-through is the manual/testnet step.

## 6. Build and commit
```bash
git add -A && git commit -m "feat(ch04): Next.js + CIP-30 wallet UI + submit endpoint"
git tag ch04
```

## 7. What is next
Chapter 05 wraps up: point everything at a public testnet, note mainnet, and list the optional
extensions (datum storage, NFT receipt, a fee + pin tier, curator moderation) and what we simplified.

## Glossary (Chapter 04)
- **CIP-30** - the browser-wallet standard (`window.cardano.<wallet>.enable()` and friends).
- **signTx (partial)** - the wallet signs and returns a witness set, not a full transaction.
- **Witness set** - the signatures; the backend attaches it to the unsigned tx to complete it.
- **Rewrite/proxy** - Next forwards `/api/*` to the Java backend so the browser calls it same-origin.
- **Change address** - an address the wallet controls; we use it as the post's payer (hex -> bech32).
