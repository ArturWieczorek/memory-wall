# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: LIVE + iterating. Core (Ch 00-06) complete and deployed (repo public; UI on GitHub Pages;
  backend runnable from home via a tunnel). Now adding polish/feature chapters from docs/BACKLOG.md:
  Ch 07 (UX + first UI tests) DONE; Ch 08 (verified author identity) and Ch 09 (CI + free GitHub
  security) next.
- (Historical) Ch 06 adds hardening for public self-hosting from a home box:
  /health + UI status light, CORS, per-IP rate limit, display-side blocklist moderation, a
  Blockfrost read-only fallback when the backend is down, and runtime-configurable backend URL.
  Backend tests green (incl. new RateLimiter/Blocklist/moderation/CORS/health); UI typechecks + next
  build clean.
- Current chapter: none - project complete. Images are text-only for now; a detailed future design is
  captured in docs/future-images.md (link+hash, IPFS, click-to-load, admin approval queue,
  free NSFW/CSAM detection tools, legal shape).
- Last updated: 2026-07-12
- Environment: Java 21 + Gradle wrapper 8.10.2 (reused). bloxbean 0.7.2. Spring Boot 3.4.13. UI:
  Next.js 14.2.35 + React 18.3.1 + TS.

### Next steps (optional, not built)
1. HOST IT PUBLICLY -> read `infra/AGENT-HANDOFF.md`. All 3 pre-hosting config gaps are now DONE
   (2026-07-13): provider project id configurable, UI static export + Pages workflow, CVE re-check
   (Spring Boot 3.4.13 -> 3.5.16). Repo is now PUBLIC and the UI auto-deploys to GitHub Pages via
   `.github/workflows/deploy-ui.yml`. To make it FULLY functional (not just rendered): run the backend
   (bound to localhost) with a real preprod Blockfrost key (WALL_BACKEND_URL + WALL_BACKEND_PROJECT_ID)
   + `tailscale funnel 8090` + set ui/public/config.js `__WALL_API__` to the tunnel URL + set
   WALL_CORS_ORIGINS to the Pages URL. Until then the page loads but shows "server offline" (the
   read-from-chain fallback still works if a visitor pastes their own Blockfrost key). The wall runs on
   ONE network per deployment (preprod); the server needs no key/funds - posters use their own wallets.
2. FEATURE ROADMAP -> `docs/BACKLOG.md` is now the single canonical backlog (planned / in progress /
   done / out-of-scope, with rationale). In flight (2026-07-13): Ch 07 Polish the wall (UX), Ch 08
   Verified author identity, Ch 09 Keep it healthy (CI + free GitHub security: Dependabot, CodeQL,
   secret scanning). Decided OUT of scope for this project: NFT receipt (CIP-25) and dApp/datum - they
   do not serve "post + read messages" and are better taught by other portfolio projects. Images stay
   deferred (designed in docs/future-images.md; SSRF + legal weight).

## Chapter status board
Legend: [ ] not started - [~] in progress - [x] done - [blocked] blocked

| Ch | Title | Status | Tag | Notes |
|----|-------|--------|-----|-------|
| 00 | Orientation | [x] | ch00 | scaffold + web-UI architecture |
| 01 | Post a message (metadata + chunking) | [x] | ch01 | WallPost + Wall (chunk to 64B, label 1719); 8 tests |
| 02 | Read the feed | [x] | ch02 | Feed.newestFirst + FeedReader/BlockfrostFeedReader (label query) |
| 03 | Backend API (Spring Boot) | [x] | ch03 | WallController: GET /feed, POST /posts/build; MockMvc tests |
| 04 | Web UI (Next.js + CIP-30 wallet) | [x] | ch04 | SubmitService + /posts/submit; ui/ Next.js page; typechecks |
| 05 | Testnet + wrap-up (+ optional extensions) | [x] | ch05 | preprod/mainnet config; extensions + simplifications documented |
| 06 | Serve it from home (networking + hardening) | [x] | ch06 | /health + status light, CORS, rate limit, blocklist, chain read-fallback, runtime config; beginner networking chapter |
| 07 | Polish the wall (UX + UI tests) | [x] | ch07 | dark/light theme, time-ago, byte counter, view-tx link, network label, empty state; Vitest+RTL (19 UI tests); WallPost.txHash |

## Pinned tool versions
| Tool | Version |
|------|---------|
| Java | 21 |
| Gradle (wrapper) | 8.10.2 |
| bloxbean cardano-client-lib | 0.7.2 |
| JUnit / AssertJ | 5.11.3 / 3.26.3 |
| Spotless / google-java-format | 6.25.0 / 1.22.0 |
| Vitest / RTL / jsdom (UI tests) | 2.x / 16.x / 25.x |

## Decisions and deviations (append-only)
- 2026-06-30 - Front end = Next.js + CIP-30 wallet (user choice). Architecture: Java/Spring backend builds an UNSIGNED post tx + serves the feed; the browser wallet signs + submits (no server keys). Metadata-first (label 1719); messages chunked to 64-byte text values. (An earlier mis-click briefly selected CLI; corrected to web UI.)

## Session log
### 2026-07-13 - Ch 07 Polish the wall (UX) + first UI tests
- Backlog reshaped: `docs/BACKLOG.md` is now canonical. NFT receipt + dApp/datum marked OUT of scope
  (no functional benefit for a message wall; better taught by other portfolio projects). Building
  B (UX), C (identity, Ch08), D-minus-systemd (CI + free GitHub security, Ch09).
- New UI test stack (dependency decision): Vitest 2 + React Testing Library 16 + jsdom 25 +
  @vitejs/plugin-react 4 (dev only; bumped @types/node to ^22 for the Vite peer). `npm test`.
- Ch 07 shipped (tag ch07): extracted pure helpers to app/lib.ts (byteLength, relativeTime,
  explorerTxUrl, rowToPost, theme logic) with 15 unit tests; presentational FeedList with 4 RTL
  tests. UI: dark/light theme (CSS vars + data-theme + no-flash init), network label, byte counter,
  time-ago timestamps, "view tx" explorer link, friendly empty state. Backend: WallPost gained an
  optional txHash (3-arg convenience ctor keeps callers compiling); BlockfrostFeedReader fills it;
  flows to feed JSON. Backend 27 tests, UI 19 tests, typecheck + static build all green.

### 2026-07-13 - close hosting gaps 2-3, go public, deploy UI to Pages
- CVE re-check vs live advisories: Spring Boot 3.4.13 (EOL OSS) -> 3.5.16 (supported; April-2026
  fixes) + testRuntimeOnly(junit-platform-launcher) to align the launcher Gradle 8.10.2 bundles.
  Next.js 14.2.35 / React 18.3.1 kept (already latest 14.x/18.x; outstanding Next CVEs are server-side
  and a static export runs no server; major bump to 15/16 deferred). bloxbean 0.7.2 kept (no advisory).
  26 backend tests green.
- UI static export: next.config.mjs now emits `output: 'export'` in prod, rewrites proxy dev-only,
  WALL_BASE_PATH for root (Cloudflare) vs sub-path (GitHub project Pages), NEXT_PUBLIC_BASE_PATH so
  public/config.js loads under the sub-path. Verified `next build` for both root and sub-path (correct
  single prefix on config.js + _next). Added .github/workflows/deploy-ui.yml (Pages deploy, .nojekyll).
- Final security pass on the delta: secret scan clean (no secret-named files, no key patterns);
  application.yml is all env-var-with-safe-default; delta adds no secrets. Repo-public stays SAFE.
- Made the GitHub repo PUBLIC and pushed; Pages enabled (source = GitHub Actions). The page renders
  but shows "server offline" until the backend is run + config.js __WALL_API__ points at the tunnel.
- Two beginner explainer docs live OUTSIDE the repo in /home/artur/Projects/Workspace/:
  memory-wall-security-fixes-explained.md and memory-wall-publishing-and-admin-explained.md.

### 2026-07-12 - pre-publish security audit + fixes
- Ran a dedicated review agent (secrets + exposed-backend posture). VERDICTS: repo-public = SAFE (no
  secrets in tree or 9-commit history; .gitignore clean; only note = author email becomes public);
  deploy-public = SAFE-WITH-FIXES. No SSRF, no stack-trace leak, CORS "*" ok (no credentials).
- Applied fixes 1-4 (+ tests): (1) cap message length in POST /posts/build (wall.max-message-bytes,
  default 4096) - anti-DoS; (2) rate limiter no longer trusts the spoofable first X-Forwarded-For hop
  - uses a configurable trusted header (wall.rate-limit.client-ip-header, e.g. CF-Connecting-IP) else
  the socket addr; (3) bind server to 127.0.0.1 (server.address, WALL_BIND) so only the tunnel can
  reach it; (4) cap txCbor/witness size in POST /posts/submit (wall.max-tx-chars, default 100000).
  New tests: RateLimitFilterTest + two WallApiTest rejections; all green.
- Deferred: (5) verify Spring Boot / bloxbean / Next.js versions against current CVEs before going
  live (agent had no CVE feed). Still NOT published - publish is user-gated.
- Beginner explainer of the 4 fixes written to /home/artur/Projects/Workspace/memory-wall-security-fixes-explained.md.

### 2026-07-12 - Ch 06: self-hosting hardening + networking teaching chapter
- Backend (no new deps): GET /api/health; CORS for /api/** (wall.cors-allowed-origins, default *);
  per-IP fixed-window rate limit -> 429 (RateLimiter + RateLimitFilter, reads X-Forwarded-For behind
  a tunnel; wall.rate-limit.*); display-side Blocklist moderation applied when serving the feed
  (wall.blocklist) - hides from OUR feed, cannot delete from chain.
- UI: green/red backend status light (polls /health), offline banner + Post disabled when down;
  optional Blockfrost project-id read-fallback (reads label-1719 posts straight from the chain when
  the backend is offline; read-only); backend URL made runtime-configurable via public/config.js
  (window.__WALL_API__/__WALL_NETWORK__), so no rebuild to change it.
- Chapter 06 = a beginner-first networking + home-hosting guide (client/server, IP/port, localhost,
  NAT, tunnels/Funnel/Cloudflare, HTTPS, CORS+preflight, health/graceful degradation, rate limiting,
  moderation, runtime config) with analogies, snippets, and step-by-step commands.
- Captured images as a future extension: docs/future-images.md (user-hosted HTTPS/IPFS links,
  content hashing for integrity, click-to-load, admin approval queue, free NSFW/CSAM detection tools,
  and the legal/responsibility shape).
- Tests: RateLimiterTest, BlocklistTest, WallModerationTest + WallApiTest health/CORS; backend green,
  UI tsc + next build clean. NOT yet made public - do a full secrets/security audit first.

### 2026-06-30 - kickoff
- Did: scaffolded repo (reused wrapper/gitignore), CLAUDE.md + PROGRESS.md, starting Ch 00/01.
- Next: Ch 01 (post + chunking), then Ch 02/03.

### 2026-06-30 - finished (Ch 02-05)
- Did: Ch 02 feed read (Feed.newestFirst, FeedReader + BlockfrostFeedReader); Ch 03 Spring Boot API
  (WallController GET /feed + POST /posts/build, MockMvc tests); Ch 04 web/wallet UI (SubmitService +
  POST /posts/submit, Next.js 14 + CIP-30 page, hex->bech32 normalisation); Ch 05 testnet/mainnet
  config + wrap-up doc. All backend tests green; UI typechecks clean; ASCII-clean; tags ch02..ch05.
- Project COMPLETE (core). Optional extensions documented but not built.
- Next: portfolio project #3, token-faucet, when requested.
