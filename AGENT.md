# AGENT.md - Memory Wall (single source of context)

> This is the one doc a fresh agent needs to get oriented and continue work. It absorbs what used to
> be split across `CLAUDE.md`, `PROGRESS.md`, and `infra/AGENT-HANDOFF.md`. User-facing intro is
> `README.md`; the canonical feature roadmap is `docs/BACKLOG.md`; operational runbooks are under
> `infra/`. This file links to those rather than copying them.

## 0. House rules (NON-NEGOTIABLE)
- **Plain ASCII only** in every tracked file and commit message. No em/en dashes, smart quotes,
  arrows, ellipsis, emoji, non-breaking/zero-width spaces. Use `-`, `'`, `"`, `->`, `...`. Enforced by
  machine git hooks.
- **No `Co-Authored-By` trailer** in commits (commit-msg hook rejects it). Conventional-commit types
  only (`feat|fix|docs|chore|test|refactor|...`).
- **TDD, keep it green.** `./gradlew spotlessApply test` (backend) and `npm run lint && npm run
  format:check && npm run typecheck && npm test && npm run build` (in `ui/`) pass before every commit.
- **One chapter = one commit + one git tag** (`ch00`, `ch01`, ...) on a single `main` branch.
- **Minimal dependencies.** Adding one needs a logged reason (here, in the session log).
- **Testnet-first, mainnet-portable.** Network + backend URL + keys come from config, never hardcoded.
- **Never commit secrets.** The Blockfrost key (`WALL_BACKEND_PROJECT_ID`) is env-only.
- Portfolio-level rules: `../../CLAUDE.md`; the exemplar course: `../usdcx-java-course/CLAUDE.md`.

## 1. What this is
A public, append-only **message wall** on Cardano: anyone posts a short message that is recorded
permanently and shown as a live feed on a web page. The value is the permanent message, not money, so
it is just as meaningful on a testnet.

Two parts:
- a **Spring Boot backend** (`src/main/java/org/wall/`) that BUILDS an unsigned posting transaction
  and SERVES the feed, and
- a **Next.js + CIP-30 UI** (`ui/`) where each visitor's browser wallet (Lace/Eternl) SIGNS and the
  backend SUBMITS.

Posts are stored as transaction **metadata under label 1719** (`Wall.java`), message chunked into
64-byte pieces (the Cardano metadata string limit). **The backend holds NO keys and needs NO funds** -
each poster signs and pays with their own wallet; the backend only needs a Cardano data provider to
read the feed and build txs.

Architecture-in-one-picture and the open metadata format: see `README.md`.

## 2. Endpoints (`WallController`)
- `GET  /api/health` - liveness (UI status light polls it).
- `GET  /api/config` - fee/pin tier state + thresholds + colour palette.
- `GET  /api/feed?limit&page` - moderated, globally pin-ordered, paginated feed (from the index).
- `GET  /api/search?q&limit&page` - full-history author/message search (from the index).
- `POST /api/posts/build` - build the UNSIGNED tx (input caps enforced; returns 400/502 with reason).
- `POST /api/posts/submit` - attach the wallet witness and submit; returns the tx hash.

## 3. Locked decisions
| # | Decision | Why |
|---|----------|-----|
| D1 | Front end = Next.js + CIP-30 wallet; off-chain = Java/Spring backend | authentic web wall; tx-building in Java (house style); the wallet signs |
| D2 | Backend builds an UNSIGNED tx; the wallet signs + submits | user keeps custody; no private keys on the server |
| D3 | Start metadata-only (no contract) | simplest permanent on-chain write; a working wall fast |
| D4 | Messages chunked to 64-byte text values | Cardano metadata string limit |
| D5 | UI uses the raw CIP-30 API (no Mesh/Lucid) | minimal UI dependencies; the backend builds the tx |
| D6 | dApp/datum + NFT receipt are optional/out-of-scope | the post+feed+UI core ships first (see BACKLOG) |
| D7 | testnet-first, network = config | mainnet is a config change |

## 4. Tech stack and pinned versions
| Tool | Version |
|------|---------|
| Java | 21 |
| Gradle (wrapper) | 8.10.2 |
| Spring Boot | 3.5.16 |
| bloxbean cardano-client-lib / backend-blockfrost | 0.7.2 |
| sqlite-jdbc (optional index store) | 3.47.1.0 |
| JUnit / AssertJ | 5.11.x / 3.26.x |
| Spotless / google-java-format | 6.25.0 / 1.22.0 |
| JaCoCo | Gradle-bundled (`jacoco` plugin) |
| Next.js / React / TypeScript | 14.2.35 / 18.3.1 / 5.9.3 |
| Vitest / RTL / jsdom | 2.x / 16.x / 25.x |
| ESLint / eslint-config-next / Prettier | 8.57.x / 14.2.35 / 3.x |

Aiken is used only if/when the optional dApp/datum version is ever built.

## 5. Repo layout
```
memory-wall/
  AGENT.md              # this file (single context/orientation source)
  README.md            # user-facing intro
  CHANGELOG.md          # release notes
  CONTRIBUTING.md / SECURITY.md / CODE_OF_CONDUCT.md
  build.gradle.kts / settings.gradle.kts / gradlew + gradle/wrapper
  src/main/java/org/wall/   # WallPost, Wall, PostTxBuilder, SubmitService, controller, index, ...
  src/test/java/org/wall/   # unit tests + LiveChainIntegrationTest (@Tag("integration"))
  src/main/resources/application.yml  # all wall.* config (env-overridable)
  ui/                       # Next.js + TypeScript + CIP-30 front end
  chapters/NN-title/README.md  # the course, one chapter per step (ch00..ch15)
  docs/                     # BACKLOG.md (canonical roadmap), future-images.md, threat analysis
  infra/                    # HOSTING.md, CLOUDFLARE-HARDENING.md, run-backend.sh
```

## 6. Current status
- **Complete and LIVE.** Chapters 00-15 done, tagged. Backend + UI green; CI + CodeQL green.
- Hosted behind a **stable Cloudflare named tunnel** (`wall.arturwieczorek.com`) on the operator's own
  domain + free Cloudflare edge hardening. UI auto-deploys to GitHub Pages
  (`.github/workflows/deploy-ui.yml`).
- Runs on **ONE network per deployment** (config, not user-selectable, or the feed would fragment
  across chains). Public target: **preprod**. Mainnet is a pure config change.
- **Canonical roadmap: `docs/BACKLOG.md`** (planned / done / out-of-scope, with reasons). The only real
  feature left is **image posts + admin approval queue** - DEFERRED; designed in
  `docs/future-images.md`, threat-analysed in `docs/IMAGE-POSTS-THREAT-ANALYSIS.md` (link-only,
  imgHash-pinned, default-deny, tailnet-bound admin, no server fetch/ML; needs Terms + legal posture).
  Out of scope: NFT receipt (CIP-25), dApp/datum (better taught by other portfolio projects).

Chapter tags: `ch00` orientation, `ch01` post+chunk, `ch02` read feed, `ch03` Spring API, `ch04`
web/wallet UI, `ch05` testnet+wrap-up, `ch06` serve-from-home hardening, `ch07` UX polish + UI tests,
`ch08` verified author identity, `ch09` CI + GitHub security, `ch10` search + precise moderation,
`ch11` fee+pin tier, `ch12` pin colour palette, `ch13` pagination, `ch14` indexer (full-history search
+ global pinning), `ch15` durable index (optional SQLite store). See each `chapters/NN-*/README.md`.

## 7. Security and hardening posture
- **CORS** for `/api/**` (`wall.cors-allowed-origins`, default `*`; safe - no credentials). `WallConfig`.
- **Per-IP rate limit** -> 429 (`RateLimiter` + `RateLimitFilter`). Client IP from
  `wall.rate-limit.client-ip-header` (a trusted proxy header, e.g. `CF-Connecting-IP`) or the socket
  address; it deliberately does NOT trust the spoofable first `X-Forwarded-For` hop.
- **Display-side moderation** - `Blocklist` hides feed posts matching `wall.blocklist` (terms) or
  `wall.blocked-tx-hashes` (exact). Cannot delete from chain - documented honestly.
- **Input caps** - `POST /posts/build` rejects a message over `wall.max-message-bytes` (4096);
  `POST /posts/submit` rejects `txCbor`/`witness` over `wall.max-tx-chars` (100000).
- **Fee-address fail-fast** - `WallConfig` refuses to start if the fee tier is on with an invalid
  `WALL_FEE_ADDRESS` (`Addresses.isValid`), so posts do not all fail with an opaque error.
- **Build errors surfaced** - `/posts/build` returns the provider's reason (502), never a stack trace.
- **Localhost bind** - `server.address` defaults to `127.0.0.1` (`WALL_BIND`), so only the tunnel can
  reach the backend, not the LAN.
- **No server keys / no funds** (D2) - a design invariant. Do not add a server key.
- Report a vulnerability: see `SECURITY.md`.

## 8. Configuration (env-overridable; see `application.yml`)
Key `wall.*` vars: `WALL_BACKEND_URL`, `WALL_BACKEND_PROJECT_ID` (SECRET), `WALL_CORS_ORIGINS`,
`WALL_RATE_LIMIT_ENABLED`/`_RPM`, `WALL_CLIENT_IP_HEADER`, `WALL_FEE_ADDRESS`, `WALL_MIN_FEE_LOVELACE`,
`WALL_PIN_FEE_LOVELACE`, `WALL_MAX_PINNED`, `WALL_PIN_DURATION_SECONDS`, `WALL_BLOCKLIST`,
`WALL_BLOCKED_TX_HASHES`, `WALL_INDEX_DB_PATH` (optional SQLite persistence), `WALL_INDEX_REFRESH_MS`,
`WALL_PORT`, `WALL_BIND`. A fuller table lives in `README.md`.

## 9. Hosting / deploy (summary; full runbook in infra/)
The UI auto-deploys to GitHub Pages. To make the wall fully functional, run the backend on your own box
(bound to localhost) with a preprod Blockfrost key and expose only its port through a tunnel:
```bash
export WALL_BACKEND_PROJECT_ID=preprod...   # secret; env only
./infra/run-backend.sh
# then a Cloudflare named tunnel (recommended) or Tailscale Funnel; point ui/public/config.js
# __WALL_API__ at the tunnel URL. Behind Cloudflare set WALL_CLIENT_IP_HEADER=CF-Connecting-IP.
```
Full step-by-step (tunnels, run modes, systemd, fee tier): `infra/HOSTING.md`. Free Cloudflare edge
hardening (rate limit, bots, DDoS): `infra/CLOUDFLARE-HARDENING.md`.

## 10. Going to mainnet
Switch the backend URL + network in config; posting/reading logic and the UI are identical. Users hold
their own keys (the wallet signs), so no server-side key handling changes.

## 11. Gotchas a fresh agent should know
- **Live backend runs older code until restarted.** New backend features (e.g. `/api/search`, the
  SQLite store) need `git pull` + restart on the home box; the Pages UI deploys automatically.
- **Fee address must differ from posters' wallets.** If the operator's fee address is also a posting
  wallet, its own change output counts as a "tip" and every post looks pinned.
- **Blockfrost vs Koios in the browser:** Blockfrost is CORS-friendly (the UI read-fallback uses it);
  Koios is CORS-blocked in browsers.
- **Rate-limit IP behind Funnel** may be one socket address for all visitors (coarse but safe). Behind
  Cloudflare, set `WALL_CLIENT_IP_HEADER=CF-Connecting-IP`.
- **Integration tests** (`LiveChainIntegrationTest`, `@Tag("integration")`) self-skip without
  `WALL_IT_BACKEND_URL`; run them with `./gradlew integrationTest`. The normal `test` task excludes
  them.
- **Beginner explainers** live OUTSIDE the repo in `/home/artur/Projects/Workspace/`.

## 12. How to continue right now
Read this file -> `docs/BACKLOG.md` for the next item -> TDD -> keep it green -> update the session log
below -> commit + tag (if a chapter). Pause for user review at each chapter boundary.

---

## Session log (append-only)

### 2026-07-15 - Repo hygiene / professional pass
- Ran a Plan-agent audit (README vs the chain-indexer bar; formatters/linters; PR-gated CI;
  tags/releases; test coverage; committed secrets; docs consolidation). Clean bill on secrets.
- Consolidated agent docs into this single `AGENT.md` (absorbed CLAUDE.md + PROGRESS.md +
  infra/AGENT-HANDOFF.md); left a one-line CLAUDE.md stub pointing here.
- UI tooling: added ESLint (`next lint`) + Prettier (+ `lint`/`format`/`format:check`/`test:coverage`
  scripts) and wired lint + format:check into CI. Backend: JaCoCo coverage report (`jacocoTestReport`,
  finalizes `test`).
- Made the promised integration-test machinery real: `LiveChainIntegrationTest` (@Tag("integration"),
  self-skips without WALL_IT_BACKEND_URL) covers PostTxBuilder build + BlockfrostFeedReader read.
- Governance/professional files: CONTRIBUTING.md, SECURITY.md, CODE_OF_CONDUCT.md, CHANGELOG.md,
  .editorconfig, .gitattributes, PR template. README: TOC, config-reference table, sample feed JSON,
  troubleshooting, releases/versioning. Cut release v1.0.0.

### 2026-07-15 - Ch 15 Durable index (optional SQLite store)
- New PostStore seam so WallIndex does not care HOW posts are stored. NoopPostStore (default:
  in-memory only = unchanged Ch14 behaviour) vs SqlitePostStore (opt-in: single SQLite file, posts
  table keyed by tx_hash, INSERT OR IGNORE, ORDER BY timestamp DESC). WallConfig.postStore picks by
  wall.index.db-path.
- WallIndex seeds from store.loadAll() on startup (warm start; best-effort) and store.save(newlyAdded)
  after each refresh. Dependency org.xerial:sqlite-jdbc 3.47.1.0, inert unless db-path set.
- Tests: SqlitePostStoreTest (temp-file roundtrip, idempotent, survives-restart) + WallIndexTest
  (seeds, saves-only-new, tolerates load failure). Tag ch15. Also flipped "Stable public hosting" to
  done and added docs/IMAGE-POSTS-THREAT-ANALYSIS.md.

### 2026-07-15 - Ch 14 Indexer (full-history search + global pinning)
- In-memory WallIndex caches ALL posts (keyed by txHash, incremental scheduled refresh). /api/feed
  pins GLOBALLY; new /api/search over all history. UI search hits the backend (debounced), offline
  falls back to the client filter over the loaded window. Feed gained pure search()+page(). Tag ch14.

### 2026-07-13 - fee-address validation (bugfix + hardening)
- Bug: a mis-typed WALL_FEE_ADDRESS (bad Bech32 checksum) made every post's tx build throw an opaque
  500. Fixes: build path returns the real reason (502); Addresses.isValid + a startup InitializingBean
  fail the app fast; net-tip fix so self-tips net to 0. Lesson: validate operator config and unit-test
  the validation even when the surrounding path needs a live chain.

### 2026-07-13 - Chapters 07-13 + Dependabot triage
- Ch07 UX polish + first UI tests (Vitest+RTL). Ch08 verified author identity (payer address from the
  tx's first input; name shown "(claimed)" + a verified short-address chip). Ch09 CI + free GitHub
  security (CI, Dependabot 3 ecosystems, CodeQL, secret scanning + push protection, MIT LICENSE).
  Ch10 client-side search + tx-hash curator moderation. Ch11 fee+pin tier (stateless competitive
  time-limited auction, verified on-chain). Ch12 payer-picked pin colour palette (on-chain `c`,
  safe-validated). Ch13 pagination / load-more.
- Dependabot's first run opened 10 major-bump PRs (Next 15/16, React 19, Spring Boot 4, Gradle 9,
  Vitest 4, ...); CI flagged the breakers; closed all 10 and added ignore rules so only minor/patch is
  auto-proposed (security updates unaffected).

### 2026-07-12/13 - go public, deploy UI, hosting gaps closed
- Closed the 3 pre-hosting gaps: provider project id configurable (WALL_BACKEND_PROJECT_ID); UI static
  export for Pages (output:'export', WALL_BASE_PATH for root vs sub-path) + deploy-ui.yml; dependency
  CVE re-check (Spring Boot 3.4.13 -> 3.5.16; Next 14.2.35 / React 18.3.1 kept - outstanding Next CVEs
  are server-side and a static export runs no server; bloxbean 0.7.2 no advisory).
- Pre-publish security audit (secrets + exposed-backend posture): repo-public SAFE, deploy SAFE with
  the applied fixes (message-size cap, robust client-IP, localhost bind, tx-size cap).

### 2026-07-12 - Ch 06 serve-it-from-home (networking + hardening)
- Backend (no new deps): /api/health; CORS; per-IP rate limit -> 429; display-side Blocklist. UI:
  status light, offline banner, Blockfrost read-only fallback, runtime-configurable backend URL. A
  beginner-first networking + home-hosting chapter. Captured images as a future extension
  (docs/future-images.md).

### 2026-06-30 - kickoff + core (Ch 00-05)
- Scaffolded the repo; Ch00 orientation; Ch01 post+chunking (label 1719); Ch02 feed read; Ch03 Spring
  Boot API (build + MockMvc tests); Ch04 web/wallet UI (SubmitService + submit; Next.js + CIP-30;
  hex->bech32); Ch05 testnet/mainnet config + wrap-up. Core complete; ASCII-clean; tags ch00..ch05.
