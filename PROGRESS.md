# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: COMPLETE. Ch 00-05 done and tagged. Backend tests green; UI typechecks clean.
- Current chapter: none - project complete (core metadata wall + web/wallet UI + testnet wrap-up).
- Last updated: 2026-06-30
- Environment: Java 21 + Gradle wrapper 8.10.2 (reused). bloxbean 0.7.2. UI: Next.js 14.2.35 + React 18.3.1 + TS.

### Next steps (optional, not built - documented in Ch 05)
1. dApp/datum version (posts at a script address -> on-chain rules).
2. NFT receipt per post (CIP-25). 3. Fee + pin tier. 4. View-only curator moderation.
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
