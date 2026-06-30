# Chapter 05 - Testnet, Mainnet, and Wrap-up

> Goal: point the wall at a public testnet, note what changes for mainnet, sketch the optional
> extensions, and be honest about what we simplified. Final chapter.

## 1. Going to a public testnet (preprod/preview)

Only configuration changes:
- **Backend:** set `WALL_BACKEND_URL` to a preprod Blockfrost endpoint (project id from
  blockfrost.io) or your own Ogmios+Kupo. Start it: `./gradlew run`.
- **UI:** set `WALL_BACKEND` (used by `next.config.mjs`) to the backend URL; `npm run dev`.
- **Wallet:** switch Lace/Eternl to preprod and fund it from the
  [preprod faucet](https://docs.cardano.org/cardano-testnets/tools/faucet).

Post from the browser; the message lands on preprod and shows up in the feed (and in any explorer
that renders metadata label 1719).

## 2. Going to mainnet

Same: point the backend URL + wallet at mainnet. The build/sign/submit logic and the UI are
identical (the user already holds their keys via the wallet, so nothing key-related changes on the
server). The honest caveat below about a public, permanent wall matters more on mainnet.

## 3. Optional extensions (described, not built)

The metadata wall is complete. To go further:
- **dApp / datum storage:** store each post as a UTxO at a script address with the message as an
  inline datum, instead of metadata. That unlocks on-chain rules (below) but costs more per post.
- **NFT receipt:** mint a CIP-25 token per post as a collectible receipt (the same pattern as the
  Proof-of-Existence certificate).
- **Fee + pin tier:** require the post to pay >= X to a fee address; a higher "pin" fee marks a post
  to show first. The backend would enforce the fee output when building.
- **Curator moderation:** keep a curator-controlled list of hidden transaction hashes; the feed
  filters them out. On-chain data is permanent, so moderation is **view-only** - you can hide a post
  from the rendered wall, never erase it.

## 4. What this course simplified (read before reusing)

- **Metadata-only** (no smart contract for the core) - simplest and cheapest; the dApp/datum version
  is the upgrade.
- **Feed = one label query.** We read recent posts from Blockfrost's metadata-by-label endpoint;
  scaling to a large wall wants pagination or a dedicated indexer.
- **No spam economics** beyond the optional fee; **moderation is view-only**.
- **Submit assumes the wallet is the only signer** (we replace the witness set). True for this simple
  self-payment; a multi-party tx would need real witness-merging.
- **The wallet click-through is manual** - the UI typechecks and the backend is unit-tested, but the
  end-to-end post needs a real browser + wallet on a network.

## 5. Privacy and safety recap
- A wall post is **public and permanent**. Only post what you are comfortable being on-chain forever.
- The server never holds your keys; **you sign in your wallet**.
- The message lives on-chain; the "trust" is simply that the ledger keeps it unchanged and timestamped.

## 6. You did it

From "what is metadata" to a working, testnet-ready, mainnet-portable memory wall: a Java/Spring
backend (build + feed + submit), a Next.js + CIP-30 wallet UI, and tests at every layer - the second
project in the portfolio, complete in the house style.

```bash
git add -A && git commit -m "feat(ch05): testnet config + wrap-up (project complete)"
git tag ch05
```

## Glossary (Chapter 05)
- **preprod/preview** - public Cardano test networks.
- **Faucet** - hands out free testnet funds.
- **dApp/datum version** - storing posts at a script address to enable on-chain rules.
- **View-only moderation** - hiding posts from the rendered feed without (being able to) erase them.
- **Indexer/pagination** - what a large feed needs beyond a single label query.
