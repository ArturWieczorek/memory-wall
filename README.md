# Memory Wall - a public message wall on Cardano

[![CI](https://github.com/ArturWieczorek/memory-wall/actions/workflows/ci.yml/badge.svg)](https://github.com/ArturWieczorek/memory-wall/actions/workflows/ci.yml)
[![CodeQL](https://github.com/ArturWieczorek/memory-wall/actions/workflows/codeql.yml/badge.svg)](https://github.com/ArturWieczorek/memory-wall/actions/workflows/codeql.yml)

Post a short message that is recorded **permanently** on Cardano and shown as a live feed on a web
page. The message is stored as transaction metadata - no smart contract, no token, no fee to read.
The value is the permanent message, not money, so it is just as meaningful on a testnet as on mainnet.

- **Visit the wall (no install, no key needed to read):** https://arturwieczorek.github.io/memory-wall/
- **The format is open:** a post is just a Cardano transaction with metadata under **label 1719** -
  any wallet or `cardano-cli` can write one (see [The open format](#the-open-format-label-1719)).
- **Post it yourself:** connect a CIP-30 wallet (Lace/Eternl) on preprod and write to the wall; your
  wallet signs and pays, the backend never holds keys.

This is also a step-by-step, test-driven **course**: each chapter under `chapters/NN-*/README.md`
builds one piece, and each is one git commit + tag (`ch00` ... `ch13`). Start at
`chapters/00-orientation/README.md`.

---

## What it is (in one picture)

```
  A VISITOR'S BROWSER                             CARDANO  (preprod / mainnet)
  +------------------------------+                +-----------------------------------+
  |  Next.js UI + CIP-30 wallet  |  --- build --> |  transaction metadata, label 1719 |
  |  (hosted on GitHub Pages)    |                |  { a: author, m: message, ts }    |
  |  signs + submits the tx      |  <-- feed ---  |  message chunked to 64-byte parts |
  +------------------------------+                +-----------------------------------+
               |   ^                                          ^
     build the |   | serve the feed                          | a block confirms the post,
   unsigned tx |   |                                          | and it appears on the wall
               v   |                                          |
  +------------------------------+                            |
  |  Java / Spring backend       | ---------- submits --------+
  |  (your box; NO keys/funds)   |
  +------------------------------+
```

- The **backend builds an unsigned transaction** and serves the feed. It holds **no keys and no
  funds**.
- The **browser wallet signs and submits** - each poster keeps custody and pays their own fee.
- **Testnet-first, mainnet-portable:** the network is configuration (default **preprod**).

## The open format (label 1719)

A post is a normal Cardano transaction carrying this note in its metadata under label **1719**:

```json
{
  "1719": {
    "a": "<author name - optional, <= 64 bytes>",
    "m": ["<message, split into 64-byte chunks>", "..."],
    "ts": "<ISO-8601 timestamp>",
    "c": "<optional pin colour: rose|mint|sky|lemon|lilac|peach>"
  }
}
```

`m` is a single string, or an array of chunks because each metadata string is capped at 64 bytes.
That is the entire contract - **anyone can write it**; this app is a convenience, not a gatekeeper.
The feed is simply "every transaction carrying label 1719".

## Try it right now
1. Open the **[live wall](https://arturwieczorek.github.io/memory-wall/)**. When the backend is up
   (green status light) the feed shows existing posts.
2. To **post**: set a CIP-30 wallet (Lace/Eternl) to **Preprod**, fund it from the
   [Cardano preprod faucet](https://docs.cardano.org/cardano-testnets/tools/faucet), then connect ->
   write -> **Post to the wall**.
3. If the light is **red** (backend offline) you can still **read**: expand "Read posts directly from
   the chain" and paste a free Blockfrost preprod project id.

## Post without this app (the format is open)

Because a post is just label-1719 metadata, you can write one from **any metadata-capable wallet**
(Typhon, Eternl, ...) or **`cardano-cli`** - no backend, nothing from this repo. Put the JSON above
under label `1719` in a self-payment transaction and submit it; it appears on the wall like any other
post. This is why the wall is permissionless - and why the author name is only a claim (below).

## Run it locally (build from source)

Backend (Java 21, Gradle wrapper):
```bash
./gradlew spotlessApply test     # format + run unit tests
./gradlew run                    # start the API on http://127.0.0.1:8090 (Ctrl+C to stop)
curl 127.0.0.1:8090/api/health   # -> {"status":"ok"}
```
UI (Next.js):
```bash
cd ui
npm install
npm run dev                      # http://localhost:3000 (proxies /api to the backend)
npm test                         # Vitest unit/component tests
npm run typecheck                # tsc --noEmit
npm run build                    # static export into ui/out (what GitHub Pages serves)
```

## Host it (backend + tunnel)

The UI auto-deploys to GitHub Pages via `.github/workflows/deploy-ui.yml`. To make the wall fully
functional you run the backend on your own box and expose **only** its port through a tunnel (no VPS,
no port-forwarding, home IP hidden). With a free preprod Blockfrost key:
```bash
export WALL_BACKEND_PROJECT_ID=preprodXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX  # secret; env only, never commit
./infra/run-backend.sh                                                 # runs with preprod defaults
```
Then pick a tunnel and point `ui/public/config.js` `__WALL_API__` at its URL:
- **Quick tunnel** (throwaway test): `cloudflared tunnel --url http://localhost:8090`
- **Cloudflare named tunnel** (stable custom URL on your own domain) - recommended
- **Tailscale Funnel** (stable URL, no domain)

Full step-by-step for each - including running the tunnel **on demand, in the background, or as a
boot service (systemd)**, an optional backend systemd unit, and the fee/pin tier - is in
**[infra/HOSTING.md](infra/HOSTING.md)**.

## Architecture
- **On-chain:** transaction metadata under label 1719 (no smart contract). Message chunked to 64-byte
  values; optional pin colour in `c`.
- **Backend** (`src/main/java/org/wall/`): Spring Boot. Builds the unsigned post tx, serves and
  moderates the feed, enriches each post with its verified payer address + any tip. Holds no keys.
  Endpoints: `GET /api/feed`, `POST /api/posts/build`, `POST /api/posts/submit`, `GET /api/health`,
  `GET /api/config`.
- **UI** (`ui/`): Next.js static export + a raw CIP-30 wallet connection; the wallet signs/submits.
- **Provider:** a Blockfrost-compatible backend (preprod by default; Yaci DevKit locally).

## Going to mainnet
Point the backend at a mainnet provider (`WALL_BACKEND_URL` + a mainnet `WALL_BACKEND_PROJECT_ID`) and
set `window.__WALL_NETWORK__ = "mainnet"` in `ui/public/config.js`. The posting/reading logic and the
UI are identical - posters simply spend real ADA on fees. Details in `infra/HOSTING.md`.

## What the wall guarantees (and what it does not)

Guarantees:
- **Permanence** - once confirmed, a post is on-chain forever; nobody (including you) can edit or
  delete it.
- **Verifiable poster** - the feed shows the address that actually **signed** each post, linked to a
  block explorer. That identity cannot be faked.

Does NOT:
- **The author *name* is a claim** - free text; anyone (especially via `cardano-cli`) can type any
  name. Trust the verified address, not the name.
- **Moderation is display-side only** - the blocklist / blocked-tx-hashes hide posts from *this* feed;
  they cannot remove anything from the chain, and other frontends still show them.
- **Search + pinning act on the loaded window** - not the full history (that needs an indexer; see
  `docs/BACKLOG.md`).
- **Post only what you are comfortable being public and permanent.**

## The course

Each chapter is a self-contained lesson under `chapters/` (one git tag each, `ch00` ... `ch13`):

| Ch | Title |
|----|-------|
| 00 | Orientation - the wall + the backend-builds / wallet-signs architecture |
| 01 | Post a message - metadata + chunking to 64 bytes (TDD) |
| 02 | Read the feed - parse posts back, newest first |
| 03 | Backend API - Spring Boot (build-tx + feed endpoints) |
| 04 | Web UI - Next.js + CIP-30 wallet |
| 05 | Testnet + wrap-up - preprod config, mainnet notes, optional extensions |
| 06 | Serve it from home - networking + hardening (tunnels, CORS, rate limit, moderation) |
| 07 | Polish the wall - dark mode, time-ago, byte counter, view-tx link (+ first UI tests) |
| 08 | Verified author identity - the signing address beside the claimed name |
| 09 | Keep it healthy - CI, Dependabot, CodeQL, secret scanning, LICENSE |
| 10 | Search + precise moderation - client-side feed search; hide a post by tx hash |
| 11 | Fee + pin tier - tip to post, tip more to pin (scarce, competitive, time-limited) |
| 12 | Pin colour palette - payer picks a pastel, stored on-chain |
| 13 | Pagination - "Load more" |

Roadmap and out-of-scope decisions (with reasons): `docs/BACKLOG.md`.

## Tech stack
- **Backend:** Java 21, Gradle, Spring Boot, bloxbean cardano-client-lib (+ backend-blockfrost),
  JUnit 5 / AssertJ, Spotless.
- **UI (`ui/`):** Next.js + TypeScript + React, raw CIP-30 wallet API (no Cardano JS lib - the backend
  builds the tx); Vitest + React Testing Library for tests.

## Repo layout
```
src/main/java/org/wall/   backend: WallPost, Wall (metadata + tx build), Spring API, feed reader
ui/                       Next.js + CIP-30 UI (static-exported to GitHub Pages)
chapters/NN-title/        the course, one chapter per step
infra/                    hosting runbook (HOSTING.md), run-backend.sh, AGENT-HANDOFF.md
docs/                     BACKLOG.md (roadmap) + future-images.md (design notes)
CLAUDE.md / PROGRESS.md    project vision/decisions + living status log
```

## License

MIT - see [LICENSE](LICENSE).
