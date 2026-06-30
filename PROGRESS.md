# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: started. Scaffold + Ch 00/01 this session (CLI memory wall, metadata-based).
- Current chapter: see board.
- Last updated: 2026-06-30
- Environment: Java 21 + Gradle wrapper 8.10.2 (reused). bloxbean 0.7.2.

### Next steps (in order)
1. Ch 02 - read the feed (parse posts back; newest-first; fetch is integration).
2. Ch 03 - Spring Boot API (build unsigned post tx + feed endpoint; MockMvc tests).
3. Ch 04 - Next.js + CIP-30 wallet UI (connect, post via wallet-sign, render feed).
4. Ch 05 - testnet + wrap-up (+ optional extensions: dApp/datum, NFT receipt, fee+pin, moderation).

## Chapter status board
Legend: [ ] not started - [~] in progress - [x] done - [blocked] blocked

| Ch | Title | Status | Tag | Notes |
|----|-------|--------|-----|-------|
| 00 | Orientation | [ ] | - | |
| 01 | Post a message (metadata + chunking) | [ ] | - | |
| 02 | Read the feed | [ ] | - | |
| 03 | Backend API (Spring Boot) | [ ] | - | build unsigned post tx + feed |
| 04 | Web UI (Next.js + CIP-30 wallet) | [ ] | - | |
| 05 | Testnet + wrap-up (+ optional extensions) | [ ] | - | |

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
