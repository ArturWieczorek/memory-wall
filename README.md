# Memory Wall

A public, append-only **message wall on Cardano**: anyone posts a short message that is recorded
permanently as transaction metadata and shown as a live feed on a web page. The value is the
permanent message, not money - so it is just as meaningful on a testnet.

**Live UI:** https://arturwieczorek.github.io/memory-wall/
(the feed is live; posting works whenever the backend is running - see [Run and host it](#run-and-host-it)).

This repo is also a **beginner-friendly, test-driven course**: each step is one chapter with its own
README, built red-green-refactor, one git tag per chapter. See [The course](#the-course).

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
npm run typecheck                # tsc --noEmit
npm run build                    # static export into ui/out (what GitHub Pages serves)
```

## Run and host it

The UI auto-deploys to GitHub Pages via `.github/workflows/deploy-ui.yml`. To make the wall fully
functional you run the backend on your own box and expose it through a tunnel (no VPS, no
port-forwarding). Full step-by-step guide: **[infra/HOSTING.md](infra/HOSTING.md)**.

In short, with a free preprod Blockfrost key:
```bash
export WALL_BACKEND_PROJECT_ID=preprod...   # your key (secret; never commit)
./infra/run-backend.sh                       # runs the backend with preprod defaults
# then expose port 8090 with a tunnel (Cloudflare quick tunnel or Tailscale Funnel)
# and point ui/public/config.js __WALL_API__ at the tunnel URL (see infra/HOSTING.md).
```

## The course

Each chapter is a self-contained lesson under `chapters/`:

| Ch | Title |
|----|-------|
| 00 | Orientation - the wall + the backend-builds / wallet-signs architecture |
| 01 | Post a message - metadata + chunking to 64 bytes (TDD) |
| 02 | Read the feed - parse posts back, newest first |
| 03 | Backend API - Spring Boot (build-tx + feed endpoints) |
| 04 | Web UI - Next.js + CIP-30 wallet |
| 05 | Testnet + wrap-up - preprod config, mainnet notes, optional extensions |
| 06 | Serve it from home - networking + hardening (tunnels, CORS, rate limit, moderation) |

## Tech stack
- **Backend:** Java 21, Gradle, Spring Boot, bloxbean cardano-client-lib (+ backend-blockfrost),
  JUnit 5 / AssertJ, Spotless.
- **UI (`ui/`):** Next.js + TypeScript + React, raw CIP-30 wallet API (no Cardano JS lib - the backend
  builds the tx).

## Repo layout
```
src/main/java/org/wall/   backend: WallPost, Wall (metadata + tx build), Spring API, feed reader
ui/                       Next.js + CIP-30 UI (static-exported to GitHub Pages)
chapters/NN-title/        the course, one chapter per step
infra/                    hosting runbook (HOSTING.md), run-backend.sh, AGENT-HANDOFF.md
CLAUDE.md / PROGRESS.md    project vision/decisions + living status log
```

## Notes
- Moderation is **display-side** (a blocklist hides posts from *this* feed); on-chain posts are
  permanent and cannot be deleted.
- Images are text-only by design for now; a full design (off-chain links, integrity hashing, an admin
  approval queue, safe rendering) is in `docs/future-images.md`.
