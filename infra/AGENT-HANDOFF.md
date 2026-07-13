# Agent handoff - hosting the Memory Wall publicly

> Read this if your task involves DEPLOYING / HOSTING the wall, or continuing the hardening work.
> For the general project, read `../CLAUDE.md` then `../PROGRESS.md` first. This file is the current,
> concrete state of "make it live" so you do not have to re-derive it.

## TL;DR state (as of 2026-07-12)
- The wall is **code-complete, hardened, tested, and security-audited**. Backend tests green; UI
  `tsc` + `next build` clean. It is **NOT deployed and NOT published** - that is user-gated.
- Audit verdicts: **repo-public = SAFE** (no secrets in tree or full git history); **deploy-public =
  SAFE** (the flagged fixes are applied).
- **All 3 pre-hosting gaps are now closed** (2026-07-13): provider project id is configurable, the UI
  builds a static export for Pages (+ a deploy workflow), and dependencies were re-checked against
  live advisories (Spring Boot bumped 3.4.13 -> 3.5.16). See "Remaining work" for the details.
- The wall runs on **ONE network per deployment** (config, not user-selectable) - a per-visitor choice
  would fragment the feed across chains. Default/target for the public wall is **preprod** (free test
  ADA; the permanent message is the value, not money). Mainnet is a pure config change.
- **Do NOT** expose the running backend service without an explicit user go-ahead. (Making the repo
  public + deploying the static UI was explicitly authorized on 2026-07-13.)

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

## Remaining work before real-network hosting
1. [DONE 2026-07-13] **Provider project id is now configurable.** `WallProperties.backendProjectId`
   (env `WALL_BACKEND_PROJECT_ID`, default `"wall"` for local Yaci); `WallConfig` uses it. VERIFIED
   locally against real preprod Blockfrost: `GET /api/feed` read a real label-1719 post and
   `POST /api/posts/build` produced a valid unsigned tx. (The key is a secret - env only, never
   committed.)
2. [DONE 2026-07-13] **UI static-export config for Pages.** `ui/next.config.mjs` now emits a static
   export in production (`output: 'export'`) and keeps the `rewrites()` proxy for dev only (a static
   export forbids rewrites; in prod the UI calls the backend directly via `window.__WALL_API__`).
   Supports both root hosting (Cloudflare Pages) and sub-path hosting (GitHub project Pages) via
   `WALL_BASE_PATH` (e.g. `/memory-wall`); `NEXT_PUBLIC_BASE_PATH` is mirrored so `public/config.js`
   loads under the sub-path (public/ files are not auto-prefixed). Verified: `next build` passes for
   BOTH root and sub-path; generated HTML references `config.js` and `_next/` with the correct single
   prefix in each case. A Pages deploy workflow was added: `.github/workflows/deploy-ui.yml` (builds
   with `WALL_BASE_PATH=/<repo>`, adds `.nojekyll`, publishes via the official Pages actions).
3. [DONE 2026-07-13] **Dependency CVE re-check** (audit finding 5). Checked against live advisories
   (July 2026): **Spring Boot 3.4.13 -> 3.5.16** (the 3.4.x OSS line is EOL and stops at 3.4.13; 3.5.16
   is the latest supported OSS 3.5.x and carries the April-2026 fixes; most of those CVEs did not
   apply anyway - no Spring Security, no DevTools). Added `testRuntimeOnly(junit-platform-launcher)`
   to align the launcher the newer JUnit needs (Gradle 8.10.2 bundles an older one). **Next.js
   14.2.35 / React 18.3.1** are already the latest of the 14.x / 18.x lines; the outstanding Next
   advisories (RSC DoS, Server Functions source disclosure, middleware/SSRF/cache-poisoning) target
   the SERVER runtime, which a static export does not run, so the deployed site is not exposed - a
   major jump to Next 15/16 + React 19 was judged not worth the breakage risk for a statically hosted
   hobby wall (revisit if ever hosted on a Next server). **bloxbean 0.7.2** - no advisory found; it is
   server-side only. Backend: 26 tests green after the bump.

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
