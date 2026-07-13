# Memory Wall

[![CI](https://github.com/ArturWieczorek/memory-wall/actions/workflows/ci.yml/badge.svg)](https://github.com/ArturWieczorek/memory-wall/actions/workflows/ci.yml)
[![CodeQL](https://github.com/ArturWieczorek/memory-wall/actions/workflows/codeql.yml/badge.svg)](https://github.com/ArturWieczorek/memory-wall/actions/workflows/codeql.yml)

A public, append-only **message wall on Cardano**: anyone posts a short message that is recorded
permanently as transaction metadata and shown as a live feed on a web page. The value is the
permanent message, not money - so it is just as meaningful on a testnet.

- **Live UI:** https://arturwieczorek.github.io/memory-wall/ (the feed is live; posting works when the
  backend is running - see [Run and host it](#run-and-host-it)).
- **The format is open:** a post is just a Cardano transaction with metadata under **label 1719** -
  any wallet or `cardano-cli` can write one (see [The open format](#the-open-format-label-1719)).

This repo is also a **beginner-friendly, test-driven course**: each step is one chapter with its own
README, built red-green-refactor, one git tag per chapter. See [The course](#the-course).

## What you can do
- **Post** a permanent message from a CIP-30 browser wallet (Lace/Eternl) - your wallet signs and pays.
- **Read** the live feed - newest first, with "time ago" timestamps, a **verified payer-address** chip,
  and a "view tx" explorer link on each post.
- **Search** the loaded feed, switch **light/dark** theme, and (if the backend is down) read posts
  **straight from the chain** with your own Blockfrost key.

## How it works

```
browser (Next.js UI) + CIP-30 wallet  <-->  Java/Spring backend  <-->  Cardano (via Blockfrost)
        signs + submits                       builds unsigned tx,          metadata label 1719,
                                               serves the feed              message chunked to 64B
```

- The **backend builds an unsigned transaction** and serves the feed. It holds **no keys and no
  funds**.
- The **browser wallet signs and submits** - each poster keeps custody and pays their own fee.
- **Testnet-first, mainnet-portable:** the network is configuration (default **preprod**); going to
  mainnet is a config change, not a rewrite.

## The open format (label 1719)

A post is a normal Cardano transaction carrying this note in its metadata under label **1719**:

```json
{
  "1719": {
    "a": "<author name - optional, <= 64 bytes>",
    "m": ["<message, split into 64-byte chunks>", "..."],
    "ts": "<ISO-8601 timestamp>"
  }
}
```

- `a` = author (a free-text **claim** - see [guarantees](#what-the-wall-guarantees-and-what-it-does-not)),
  `m` = the message (a single string, or an array of chunks because each metadata string is capped at
  64 bytes), `ts` = timestamp.
- That is the entire contract. **Anyone can write it** - this app is a convenience, not a gatekeeper
  (the wall is permissionless). The feed is simply "every transaction carrying label 1719".

## Try it right now
1. Open the **[live UI](https://arturwieczorek.github.io/memory-wall/)**. When the backend is up
   (green status light), the feed shows existing posts.
2. To **post**: set a CIP-30 wallet (Lace/Eternl) to **Preprod**, fund it from the
   [Cardano preprod faucet](https://docs.cardano.org/cardano-testnets/tools/faucet), then connect ->
   write a message -> **Post to the wall**.
3. If the status light is **red** (backend offline), you can still **read**: expand "Read posts
   directly from the chain" and paste a free Blockfrost preprod project id.

## Quick start (local development)

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

## Run and host it

The UI auto-deploys to GitHub Pages via `.github/workflows/deploy-ui.yml`. To make the wall fully
functional you run the backend on your own box and expose it through a tunnel (no VPS, no
port-forwarding). Full step-by-step guide: **[infra/HOSTING.md](infra/HOSTING.md)**.

In short, with a free preprod Blockfrost key:
```bash
# WALL_BACKEND_PROJECT_ID is your Blockfrost project id - a free API key from https://blockfrost.io
# for a "Cardano Preprod" project. It starts with "preprod". It is a SECRET: env var only, never
# commit it. (The backend uses it to read the chain and build transactions; it holds no wallet keys.)
export WALL_BACKEND_PROJECT_ID=preprodXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
./infra/run-backend.sh                       # runs the backend with preprod defaults
# then expose port 8090 with a tunnel (Cloudflare quick tunnel or Tailscale Funnel)
# and point ui/public/config.js __WALL_API__ at the tunnel URL (see infra/HOSTING.md).
```

## Post without this app (the format is open)

Because a post is just label-1719 metadata, you can write one from **any metadata-capable wallet**
(e.g. Typhon, Eternl) or **`cardano-cli`** - no backend, nothing from this repo. Put the JSON above
under label `1719` in a self-payment transaction and submit it; it appears on the wall like any other
post. This is exactly why the wall is permissionless - and why the author name is only a claim.

## What the wall guarantees (and what it does not)

Guarantees:
- **Permanence** - once confirmed, a post is on-chain forever; nobody (including you) can edit or
  delete it.
- **Verifiable poster** - the feed shows the address that actually **signed** each post (read from the
  transaction) and links it to the explorer. That identity cannot be faked.

Does NOT:
- **The author *name* is a claim** - it is free text; anyone (especially via `cardano-cli`) can type
  any name. Trust the verified address, not the name.
- **Moderation is display-side only** - the term blocklist and blocked-tx-hash list hide posts from
  *this* feed; they cannot remove anything from the chain, and other frontends still show them.
- **Search covers only the loaded window** - not the full history (that needs an indexer; see
  `docs/BACKLOG.md`).
- **Post only what you are comfortable being public and permanent.**

## Going to mainnet

Point the backend at a mainnet provider (`WALL_BACKEND_URL` + a mainnet `WALL_BACKEND_PROJECT_ID`) and
set `window.__WALL_NETWORK__ = "mainnet"` in `ui/public/config.js`. The posting/reading logic and the
UI are identical - posters simply spend real ADA on fees. Details in `infra/HOSTING.md`.

## The course

Each chapter is a self-contained lesson under `chapters/` (one git tag each, `ch00` ... `ch10`):

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

Roadmap of what is next (and what is out of scope, with reasons): `docs/BACKLOG.md`.

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
