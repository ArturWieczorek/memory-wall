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
builds one piece, and each is one git commit + tag (`ch00` ... `ch15`). Start at
`chapters/00-orientation/README.md`.

## Contents
- [What it is (in one picture)](#what-it-is-in-one-picture)
- [The open format (label 1719)](#the-open-format-label-1719)
- [Try it right now](#try-it-right-now)
- [Post without this app](#post-without-this-app-the-format-is-open)
- [Run it locally](#run-it-locally-build-from-source)
- [Host it (backend + tunnel)](#host-it-backend--tunnel)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [Sample feed response](#sample-feed-response)
- [Going to mainnet](#going-to-mainnet)
- [What the wall guarantees (and what it does not)](#what-the-wall-guarantees-and-what-it-does-not)
- [Troubleshooting](#troubleshooting)
- [The course](#the-course)
- [Releases and versioning](#releases-and-versioning)
- [Tech stack](#tech-stack)
- [Repo layout](#repo-layout)
- [Contributing and security](#contributing-and-security)
- [License](#license)

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
- **Cloudflare named tunnel** (stable custom URL on your own domain) - recommended:
  ```bash
  cloudflared tunnel login                                        # authorize your Cloudflare domain
  cloudflared tunnel create memory-wall                           # note the tunnel id + creds path
  cloudflared tunnel route dns memory-wall wall.yourdomain.com    # add the DNS record
  # write ~/.cloudflared/config.yml (tunnel id, creds file, ingress -> http://localhost:8090), then:
  cloudflared tunnel run memory-wall                              # run it (foreground)
  sudo cloudflared service install                                # or: auto-start on boot (systemd)
  ```
- **Tailscale Funnel** (stable URL, no domain)

Full step-by-step for each - the `config.yml` contents, running the tunnel **on demand, in the
background, or as a boot service (systemd)**, an optional backend systemd unit, and the fee/pin tier -
is in **[infra/HOSTING.md](infra/HOSTING.md)**. Free Cloudflare edge hardening (rate limiting, bots,
DDoS): **[infra/CLOUDFLARE-HARDENING.md](infra/CLOUDFLARE-HARDENING.md)**.

## Configuration

Everything is read from the environment with a safe default, so the backend runs with no config at
all (against a local Yaci DevKit). Override any of these via env vars; the source of truth is
`src/main/resources/application.yml`.

| Env var | Default | What it does |
|---------|---------|--------------|
| `WALL_BACKEND_URL` | `http://localhost:8080/api/v1/` | Blockfrost-compatible provider base URL (e.g. a preprod Blockfrost URL). |
| `WALL_BACKEND_PROJECT_ID` | `wall` | Provider project id / API key. **SECRET - env only, never commit.** `wall` works for local Yaci; set your real key for hosted Blockfrost. |
| `WALL_CORS_ORIGINS` | `*` | Comma-separated browser origins allowed to call the API (the hosted UI). `*` = any (safe here: no credentials). |
| `WALL_RATE_LIMIT_ENABLED` | `true` | Turn the per-IP rate limit on/off. |
| `WALL_RATE_LIMIT_RPM` | `20` | Allowed API requests per client IP per minute. |
| `WALL_CLIENT_IP_HEADER` | (blank) | Trusted proxy header carrying the real client IP (e.g. `CF-Connecting-IP` behind Cloudflare). Blank = socket address. Do NOT set to `X-Forwarded-For` (spoofable). |
| `WALL_FEE_ADDRESS` | (blank) | Operator address a post tips. Blank = fee/pin tier OFF (posting is free, the default). Must differ from posters' wallets. |
| `WALL_MIN_FEE_LOVELACE` | `0` | Minimum tip (lovelace) required to post when the fee tier is on. |
| `WALL_PIN_FEE_LOVELACE` | `0` | Tip (lovelace) at/above which a post is pinned (verified on-chain). |
| `WALL_MAX_PINNED` | `3` | How many posts may be pinned at once (highest tips win); `<=0` = unlimited. |
| `WALL_PIN_DURATION_SECONDS` | `604800` | How long a pin lasts from its timestamp (default 7 days); `<=0` = never expires. |
| `WALL_BLOCKLIST` | (blank) | Comma-separated terms; a post whose author/message contains one is hidden from this feed (display-side only). |
| `WALL_BLOCKED_TX_HASHES` | (blank) | Comma-separated tx hashes to hide exactly (display-side only). |
| `WALL_INDEX_DB_PATH` | (blank) | Optional SQLite file to persist the full-history index. Blank = in-memory only (re-ingests on restart). |
| `WALL_INDEX_REFRESH_MS` | `60000` | How often the index refreshes from the chain (ms). |
| `WALL_PORT` | `8090` | Backend HTTP port. |
| `WALL_BIND` | `127.0.0.1` | Bind address. Localhost = reachable only via the tunnel; set `0.0.0.0` only for deliberate direct LAN access. |

## Architecture
- **On-chain:** transaction metadata under label 1719 (no smart contract). Message chunked to 64-byte
  values; optional pin colour in `c`.
- **Backend** (`src/main/java/org/wall/`): Spring Boot. Builds the unsigned post tx, serves and
  moderates the feed, enriches each post with its verified payer address + any tip. Holds no keys.
  Endpoints: `GET /api/feed`, `POST /api/posts/build`, `POST /api/posts/submit`, `GET /api/health`,
  `GET /api/config`.
- **UI** (`ui/`): Next.js static export + a raw CIP-30 wallet connection; the wallet signs/submits.
- **Provider:** a Blockfrost-compatible backend (preprod by default; Yaci DevKit locally).

## Sample feed response

`GET /api/feed?limit=20&page=1` returns a JSON array, newest first (active pins first). Each post:

```json
[
  {
    "author": "alice",
    "message": "gm from the wall",
    "timestamp": "2026-07-14T09:30:00Z",
    "txHash": "0d6c517a1346...",
    "address": "addr_test1qrexrc3...",
    "tipLovelace": 5000000,
    "pinned": true,
    "color": "sky"
  }
]
```

`author` is a claim (free text); `address` is the verified signer read from the chain. `tipLovelace`
and `pinned` are 0/false unless the fee tier is on; `color` is blank unless the payer chose a pin
colour. `GET /api/search?q=...` returns the same shape across all history.

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
- **The index is in-memory** - full-history search + global pinning work, but the cache rebuilds on
  restart (a persistent store is still on `docs/BACKLOG.md`); while offline, the UI's search falls
  back to just the loaded window.
- **Post only what you are comfortable being public and permanent.**

## Troubleshooting

- **Status light is red / "server offline".** The backend is not reachable. Check it is running
  (`curl 127.0.0.1:8090/api/health`), that `ui/public/config.js` `__WALL_API__` points at your live
  tunnel URL, and that `WALL_CORS_ORIGINS` includes the UI's origin. You can still read the feed via
  the "Read posts directly from the chain" fallback (paste a free Blockfrost preprod key).
- **"Build failed" (HTTP 502) when posting.** The tx could not be built. Usual causes: the wallet has
  too little ADA to cover the post + fee; or the fee tier is on with an invalid `WALL_FEE_ADDRESS`
  (the backend refuses to start on a bad address, so re-check it); the response includes the
  provider's reason.
- **Every post shows up as PINNED.** Your `WALL_FEE_ADDRESS` is also one of the posting wallets, so
  its own change output is counted as a tip. Use a fee address that is NOT a poster wallet.
- **The UI still points at the old backend after you changed the tunnel.** `config.js` is cached by
  the browser; hard-refresh (Ctrl+Shift+R). A stable named tunnel avoids this.

## The course

Each chapter is a self-contained lesson under `chapters/` (one git tag each, `ch00` ... `ch15`):

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
| 14 | Indexer - full-history search + global pinning (in-memory cache of all posts) |
| 15 | Durable index - optional SQLite store so the index survives a restart |

Roadmap and out-of-scope decisions (with reasons): `docs/BACKLOG.md`.

## Releases and versioning
Two independent tracks:
- **Semantic-version GitHub Releases** mark shippable states - **`v1.0.0`** is the first (the
  feature-complete wall, chapters 00-15). See [CHANGELOG.md](CHANGELOG.md) and the repo's Releases.
- **Per-chapter teaching tags** `ch00 ... ch15` mark each lesson's commit, so you can check out the
  repo at any point in the course.

## Tech stack
- **Backend:** Java 21, Gradle, Spring Boot 3.5.16, bloxbean cardano-client-lib (+ backend-blockfrost),
  JUnit 5 / AssertJ, Spotless, JaCoCo.
- **UI (`ui/`):** Next.js + TypeScript + React, raw CIP-30 wallet API (no Cardano JS lib - the backend
  builds the tx); Vitest + React Testing Library for tests; ESLint + Prettier.

## Repo layout
```
src/main/java/org/wall/   backend: WallPost, Wall (metadata + tx build), Spring API, feed reader
ui/                       Next.js + CIP-30 UI (static-exported to GitHub Pages)
chapters/NN-title/        the course, one chapter per step
infra/                    hosting runbook (HOSTING.md), CLOUDFLARE-HARDENING.md, run-backend.sh
docs/                     BACKLOG.md (roadmap) + future-images.md + image threat analysis
AGENT.md                  single source of project context, decisions, status, session log
```

## Contributing and security
Contributions welcome - see [CONTRIBUTING.md](CONTRIBUTING.md) and the
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). To report a vulnerability, follow
[SECURITY.md](SECURITY.md) (please do not open a public issue). Full project context for contributors
and agents lives in [AGENT.md](AGENT.md).

## License

MIT - see [LICENSE](LICENSE).
