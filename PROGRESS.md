# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: LIVE + iterating. Core (Ch 00-06) complete and deployed (repo public; UI on GitHub Pages;
  backend runnable from home via a tunnel). Now adding polish/feature chapters from docs/BACKLOG.md:
  Ch 07-14 DONE: UX polish, verified author identity, CI + GitHub security, search + precise
  moderation, fee + pin tier, pin colour palette, pagination, indexer (full-history search + global
  pinning). Live behind a stable Cloudflare named tunnel (wall.arturwieczorek.com) + CF hardening.
  Remaining backlog: images (deferred), optional persistent index store.
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
| 08 | Verified author identity | [x] | ch08 | payer address read from tx input (WallPost.address); feed shows name (claimed) + verified short-address chip; privacy + N+1 cost documented |
| 09 | Keep it healthy (CI + free GitHub security) | [x] | ch09 | CI (backend+UI), Dependabot (3 ecosystems), CodeQL (java+ts), secret scanning + push protection, action bumps, MIT LICENSE |
| 10 | Search + precise moderation | [x] | ch10 | client-side feed search (filterPosts, loaded window only); tx-hash curator moderation (wall.blocked-tx-hashes) alongside the term blocklist |
| 11 | Fee + pin tier | [x] | ch11 | optional tip-to-post / tip-more-to-pin; scarce+competitive+time-limited pins verified on-chain; /api/config; UI tip field + rules + pinned pastel/badge; stateless (no queue) |
| 12 | Pin colour palette | [x] | ch12 | payer picks a pastel (fixed 6-colour palette) when pinning; stored on-chain (metadata c), safe-validated on write + read; rendered behind the pin; PinColors + /api/config palette |
| 13 | Pagination / load more | [x] | ch13 | FeedReader.recent(limit,page) + /api/feed?page; UI "Load more" appends next page (de-duped, short-page hides button) |
| 14 | Indexer (full-history search + global pinning) | [x] | ch14 | in-memory WallIndex (incremental refresh) caches all posts; /api/feed pins globally, new /api/search over all history; UI search hits backend (debounced), offline falls back to client filter |

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
### 2026-07-15 - Ch 14 Indexer (full-history search + global pinning)
- WallIndex: in-memory cache of ALL posts, keyed by txHash, refreshed on a schedule
  (@EnableScheduling; wall.index.refresh-ms default 60s) - INCREMENTAL: pages newest-first, stops when
  a page adds nothing new (steady state) or a short page ends history. Best-effort (provider hiccup
  keeps the last cache). Reuses BlockfrostFeedReader.pageRaw (extracted: parse+enrich, unordered).
- Controller now serves feed + search from the index: /api/feed = moderate -> Feed.forDisplay
  (GLOBAL pin ordering) -> Feed.page; new /api/search?q= = moderate -> Feed.search -> newestFirst ->
  page. Feed gained pure search() + page() (tested). Controller depends on WallIndex (tests mock it).
- UI: search box hits /api/search (debounced) when online = full history + Load more; offline falls
  back to client filterPosts over the loaded window (hint says which). Global pinning is automatic
  (UI renders whatever /api/feed returns).
- Trade-off: in-memory (re-ingests on restart); persistent store (SQLite) still deferred. Tests:
  backend 51, UI 33; typecheck + build green. Tag ch14.

### 2026-07-13 - fee-address validation (bugfix + hardening)
- Bug: with the fee tier on, a mis-typed WALL_FEE_ADDRESS (bad Bech32 checksum) made EVERY post's tx
  build throw "Invalid checksum" -> opaque 500. Root cause found from a live stack trace.
- Fixes: (1) POST /api/posts/build now catches the build error and returns the real reason (502, no
  stack trace) instead of a bare 500; (2) Addresses.isValid + a startup InitializingBean in WallConfig
  fail the app fast with a clear message if the fee tier is on and WALL_FEE_ADDRESS is not a valid
  address; (3) tests: AddressesTest (valid generated addr + rejects bad checksum/blank/null),
  WallApiTest.buildSurfacesReason, WallFeeApiTest now uses a generated valid address via
  @DynamicPropertySource. Backend 43 tests. Lesson logged: validate operator config, and unit-test
  the validation even when the surrounding tx-build path needs a live chain.

### 2026-07-13 - Ch 13 Pagination (load more)
- FeedReader.recent(limit, page) + default recent(limit)=page 1; BlockfrostFeedReader passes page to
  the provider; GET /api/feed?limit&page forwards both. UI: PAGE_SIZE=20, loadFeed resets to page 1,
  "Load more" appends the next page (de-duped by tx hash), button hidden on a short page / when
  offline. Pins + search still act on the loaded window (full-history pinning/search = indexer,
  backlog). Tests: backend 41 (feedForwardsPage + two-arg recent stubs), UI 31; typecheck+build green.
  Tag ch13. This completes the requested "Medium" set (fee+pin, pagination) + the emergent colour work.

### 2026-07-13 - Ch 12 Pin colour palette
- Payer picks a pastel from a fixed 6-colour palette (PinColors: rose/mint/sky/lemon/lilac/peach) when
  pinning. Stored on-chain as optional metadata `c`; WallPost gains color (8th field, 7-arg
  convenience ctor keeps callers compiling). Safe-validated (PinColors.normalize) on write
  (/posts/build) AND on read (feed reader + rowToPost) - defends against arbitrary on-chain values.
  /api/config exposes the palette. UI: colour swatches shown when the tip will pin; pinned posts get
  their colour's pastel (theme-aware light/dark), default pastel if none.
- Tests: backend 40 (PinColorsTest + color round-trip + feed JSON color + config palette), UI 31
  (pinColorBg + rowToPost colour). typecheck + build green. Tag ch12. Pagination is next (ch13).

### 2026-07-13 - Ch 11 Fee + pin tier
- Optional fee/pin tier (OFF by default; on when wall.fee-address set). Backend: WallProperties gains
  feeAddress/minFeeLovelace/pinFeeLovelace/maxPinned(3)/pinDurationSeconds(7d). PostTxBuilder adds a
  fee-address output when tipping; /posts/build rejects below-min tips. BlockfrostFeedReader reads the
  tip from the tx outputs (reusing the Ch08 utxo lookup) and marks eligibility. WallPost gains
  tipLovelace + pinned. Feed.forDisplay = pure, tested: top-N by tip, expiry window, demote overflow/
  expired. New /api/config exposes the tier + thresholds. Design (with the user): stateless
  competitive auction (no DB/queue), time-limited pins, scarce slots ranked by tip (bigger tip bumps).
- UI: fetch /api/config; when on, a tip field (prefilled to min) + a live "will pin" hint + plain
  pinning-rules text (real numbers); below-min tip blocks Post. Pinned posts get a pastel highlight +
  PINNED badge with the tip. lib: lovelaceToAda/adaToLovelace.
- Tests: backend 37, UI 29; typecheck + build green. Payer-chosen pastel palette (on-chain) deferred
  to Ch12; pagination is Ch13. Tag ch11.

### 2026-07-13 - Ch 10 Search + precise moderation (small wins)
- UI: lib.filterPosts (case-insensitive author/message substring over the LOADED window only - labelled
  as such); search box + "N of M loaded match" hint + no-match state in page.tsx. 3 new lib tests (UI 26).
- Backend: tx-hash curator moderation - WallProperties.blockedTxHashes (env WALL_BLOCKED_TX_HASHES),
  Blocklist hides a post whose txHash is listed (exact, case-insensitive) alongside the term blocklist.
  2 new BlocklistTest cases (backend 30). application.yml wall.blocked-tx-hashes added.
- Both display-side/permissionless-aware (search is client-only; moderation cannot erase on-chain).
  Full-history search still needs an indexer (backlog). Tag ch10.

### 2026-07-13 - Dependabot triage
- First enablement opened 10 PRs, all major bumps (Next 15/16, React 19, Spring Boot 4, Gradle 9,
  Vitest 4, jsdom 29, spotless 8) - CI flagged the breakers (Spring 4, React 19, Vitest 4). All
  reverse deliberate pins or are risky; closed all 10. Added ignore rules (gradle + npm:
  version-update:semver-major for "*") so Dependabot auto-proposes only minor/patch; majors are
  manual; security updates unaffected. GitHub Actions majors left on (routine).

### 2026-07-13 - Ch 09 Keep it healthy (CI + free GitHub security)
- Added .github/workflows/ci.yml (backend: spotlessCheck+test on Java 21; UI: npm ci+typecheck+test+
  build) on push/PR; .github/workflows/codeql.yml (java-kotlin autobuild + javascript-typescript);
  .github/dependabot.yml (gradle, npm/ui, github-actions weekly). Bumped deploy-ui.yml action majors
  (checkout v7, setup-node v6, configure-pages v6, upload-pages-artifact v5, deploy-pages v5) to clear
  the Node 20 deprecation. Added MIT LICENSE.
- Enabled via gh api (free on public repo): Dependabot vulnerability alerts + automatic security-fix
  PRs, secret scanning, secret-scanning push protection. Confirmed code-scanning default setup was
  not-configured (so the advanced CodeQL workflow does not conflict).
- Chapter 09 is a beginner guide to CI/Dependabot/CodeQL/secret-scanning + the housekeeping (action
  bumps, LICENSE) and the per-visitor rate-limit note. No new app code (verification = pipelines green).
  README got CI + CodeQL badges. Tag ch09.

### 2026-07-13 - Ch 08 Verified author identity
- Backend: WallPost gained an optional address (5th field; 3/4-arg convenience ctors keep callers
  compiling). BlockfrostFeedReader reads each post's payer address from the tx's first input
  (getTransactionUtxos), best-effort (failure -> empty, feed still renders); flows to feed JSON.
- UI: lib.shortenAddress + explorerAddrUrl (shared cardanoscanHost refactor). FeedList shows the name
  as "(claimed)" plus a "verified" short-address chip linking to cardanoscan (full address on hover).
  rowToPost sets address "" (offline fallback has no cheap way to fetch inputs -> no chip there).
- Tests: backend 28 (WallPost.address + feed JSON address); UI 23 (shortenAddress, explorerAddrUrl,
  verified-chip RTL). typecheck + static build green.
- Documented the privacy trade-off (links posts to a funded wallet) and the N+1 lookup cost (indexer
  in backlog fixes it). Tag ch08.

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
