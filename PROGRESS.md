# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: COMPLETE. Ch 00-06 done. Ch 06 adds hardening for public self-hosting from a home box:
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
1. Live public run: expose the backend via Tailscale Funnel/Cloudflare, deploy the UI to Pages, set
   WALL_CORS_ORIGINS; do a real preprod post (needs a funded wallet). SECURITY AUDIT the repo + git
   history before making it public.
2. Images (see docs/future-images.md). 3. dApp/datum version. 4. NFT receipt (CIP-25).
5. Pagination/indexer for a large feed. (Otherwise move to portfolio project #3, token-faucet.)

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

## Pinned tool versions
| Tool | Version |
|------|---------|
| Java | 21 |
| Gradle (wrapper) | 8.10.2 |
| bloxbean cardano-client-lib | 0.7.2 |
| JUnit / AssertJ | 5.11.3 / 3.26.3 |
| Spotless / google-java-format | 6.25.0 / 1.22.0 |

## Decisions and deviations (append-only)
- 2026-06-30 - Front end = Next.js + CIP-30 wallet (user choice). Architecture: Java/Spring backend builds an UNSIGNED post tx + serves the feed; the browser wallet signs + submits (no server keys). Metadata-first (label 1719); messages chunked to 64-byte text values. (An earlier mis-click briefly selected CLI; corrected to web UI.)

## Session log
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
