# Agent handoff - hosting the Memory Wall publicly

> Read this if your task involves DEPLOYING / HOSTING the wall, or continuing the hardening work.
> For the general project, read `../CLAUDE.md` then `../PROGRESS.md` first. This file is the current,
> concrete state of "make it live" so you do not have to re-derive it.

## TL;DR state (as of 2026-07-12)
- The wall is **code-complete, hardened, tested, and security-audited**. Backend tests green; UI
  `tsc` + `next build` clean. It is **NOT deployed and NOT published** - that is user-gated.
- Audit verdicts: **repo-public = SAFE** (no secrets in tree or full git history); **deploy-public =
  SAFE** (the flagged fixes are applied).
- There are **3 small remaining gaps** before hosting on a real public network (preprod/mainnet) -
  see "Remaining work" below. They are NOT yet done (the user asked for this handoff instead of
  having them implemented immediately).
- **Do NOT** make the repo public, push, or expose the service without an explicit user go-ahead.

## What this app is (so the plan makes sense)
- Two parts: a **Spring Boot backend** (`src/main/java/org/wall/`) and a **Next.js UI** (`ui/`).
- Flow: the UI (in each visitor's browser) connects a CIP-30 wallet, asks the backend to **build an
  UNSIGNED** post transaction, the **wallet signs**, and the backend **submits** it. Posts are stored
  as transaction metadata under **label 1719** (`Wall.java`), message chunked to 64-byte pieces.
- **The backend holds NO keys and needs NO funds.** Each poster signs and pays with their own wallet.
  The backend only needs a working Cardano data provider to read the feed and build txs.
- Endpoints (`WallController`): `GET /api/feed`, `POST /api/posts/build`, `POST /api/posts/submit`,
  `GET /api/health`.

## Hardening already in place (Chapter 06 + the audit fixes)
- **CORS** for `/api/**` - `wall.cors-allowed-origins` (default `*`; safe, no credentials). `WallConfig`.
- **Per-IP rate limit** -> HTTP 429 - `RateLimiter` + `RateLimitFilter`. Client IP comes from
  `wall.rate-limit.client-ip-header` (a trusted proxy header, e.g. `CF-Connecting-IP`) or, if blank,
  the socket address. It deliberately does NOT trust the spoofable first `X-Forwarded-For` hop.
- **Display-side moderation** - `Blocklist` hides feed posts matching `wall.blocklist` (cannot delete
  from chain).
- **Message size cap** - `POST /posts/build` rejects a message over `wall.max-message-bytes` (4096).
- **Tx size cap** - `POST /posts/submit` rejects `txCbor`/`witness` over `wall.max-tx-chars` (100000).
- **Localhost bind** - `server.address` defaults to `127.0.0.1` (`WALL_BIND` to override), so only the
  tunnel can reach the backend, not the LAN directly.
- **UI resilience** - a green/red status light (polls `/api/health`), an offline banner that disables
  posting, and a Blockfrost read-only fallback (the visitor pastes their own key to read the feed
  straight from the chain when the backend is down). Backend URL is runtime config in
  `ui/public/config.js` (`window.__WALL_API__`, `window.__WALL_NETWORK__`) - no rebuild to change it.

## Remaining work before real-network hosting (NOT yet done)
1. **Make the provider project id configurable.** `WallConfig` hardcodes
   `new BFBackendService(props.backendUrl(), "wall")`. `"wall"` is a placeholder that only works with
   local **Yaci DevKit**. For **preprod/mainnet Blockfrost** the project id must be the user's real
   key. Add `WallProperties.backendProjectId` (env `WALL_BACKEND_PROJECT_ID`, default `"wall"`), use
   it in `WallConfig`, document in `application.yml`. ~5 lines + a note. Without this the feed/build
   calls to real Blockfrost get 403.
2. **UI static-export config for Pages.** `ui/next.config.mjs` has a dev-only `rewrites()` proxy.
   Static hosting (Cloudflare/GitHub Pages) needs `output: 'export'` and the rewrites guarded to dev
   only (in production the UI calls the backend directly via `window.__WALL_API__`, so the proxy is
   unused). Verify `next build` still passes after the change. (Alternatively host the UI on a
   Next-capable host and skip export - then rewrites can stay but are unused in prod.)
3. **Dependency CVE re-check** (audit finding 5, no live CVE feed during the audit): verify Spring
   Boot 3.4.13, bloxbean cardano-client 0.7.2, Next.js 14.2.35, React 18.3.1 against current
   advisories; bump if needed; keep green.

## What the user must provide (accounts / infra - not code)
- A **free Blockfrost preprod project id** (blockfrost.io) - or run their own provider.
- **Tailscale** installed (for Funnel) - or Cloudflare (`cloudflared`).
- A **free UI host**: Cloudflare Pages or GitHub Pages.
- The **home box must stay on** while the wall is live.

## Deployment runbook (after the 3 gaps are closed)
```bash
# 1. Backend -> preprod Blockfrost, bound to localhost (default), UI origin allowed:
WALL_BACKEND_URL=https://cardano-preprod.blockfrost.io/api/v0/ \
WALL_BACKEND_PROJECT_ID=preprod<yourKey> \
WALL_CORS_ORIGINS=https://your-wall.pages.dev \
./gradlew run
curl http://localhost:8090/api/health   # -> {"status":"ok"}

# 2. Expose from the home box (no VPS, no port-forwarding):
tailscale funnel 8090                    # -> https://yourbox.tailXXXX.ts.net
curl https://yourbox.tailXXXX.ts.net/api/health

# 3. Point the UI at the tunnel (edit static config; no rebuild):
#    ui/public/config.js:
#      window.__WALL_API__     = "https://yourbox.tailXXXX.ts.net";
#      window.__WALL_NETWORK__ = "preprod";

# 4. Deploy the UI (static export) to Cloudflare Pages or GitHub Pages.

# 5. Visit the UI -> status light green -> connect wallet -> post. Others can post too.
#    Behind Cloudflare, also set WALL_CLIENT_IP_HEADER=CF-Connecting-IP so the rate limit is per-user.
```
Optional and separate from hosting: make the GitHub repo public (audit: SAFE).

## Gotchas / decisions a fresh agent should know
- **Server key/funds:** none needed server-side; posters use their own wallets. Do not add a server key.
- **Blockfrost vs Koios in the browser:** Blockfrost is CORS-friendly (the UI's read-fallback uses it);
  Koios is CORS-blocked in browsers (see the proof-of-existence project's finding). Backend can use
  either server-side, but the code currently uses the Blockfrost backend.
- **Rate-limit IP behind Funnel:** Tailscale Funnel may present the same socket address for all
  visitors, so the default limiter is coarse (over-limits, never under-limits) - acceptable. Behind
  Cloudflare, set `WALL_CLIENT_IP_HEADER=CF-Connecting-IP` for per-visitor limiting.
- **Moderation is display-side only** (`Blocklist`); on-chain posts are permanent. A stronger
  approval-queue model and the whole image story are designed (not built) in `../docs/future-images.md`.
- **Images:** text-only by design for now; forward-compatible metadata. See `../docs/future-images.md`
  (link+hash, IPFS, click-to-load, admin approval queue, free NSFW/CSAM tooling, legal shape).
- **Beginner explainer** of the security fixes lives OUTSIDE the repo at
  `/home/artur/Projects/Workspace/memory-wall-security-fixes-explained.md`.

## Key files
- `src/main/java/org/wall/WallController.java` - endpoints + input caps.
- `src/main/java/org/wall/WallConfig.java` - backend/provider bean + CORS (gap 1 lives here).
- `src/main/java/org/wall/WallProperties.java` + `src/main/resources/application.yml` - all `wall.*` config.
- `src/main/java/org/wall/RateLimiter.java` + `RateLimitFilter.java` - rate limiting.
- `src/main/java/org/wall/Blocklist.java` - moderation.
- `ui/app/page.tsx` - status light, offline banner, chain fallback, runtime API base.
- `ui/public/config.js` - runtime backend URL + network.
- `ui/next.config.mjs` - dev proxy (gap 2 lives here).
- `chapters/06-serve-it-from-home/README.md` - the beginner networking + hosting chapter.
