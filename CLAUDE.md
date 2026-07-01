# CLAUDE.md - Memory Wall (read this first)

> Fresh agent: read this, then `PROGRESS.md`. Build chapter by chapter, TDD, one git tag per
> chapter. House rules: `../../CLAUDE.md` (portfolio) and `../usdcx-java-course/CLAUDE.md`
> (the exemplar). ASCII only; no Co-Authored-By trailer (machine git hooks).

## 1. What this is
A public, append-only **message wall** on Cardano: anyone posts a short message that is recorded
permanently and is shown as a live feed on a web page. The value is the permanent message, not money,
so it is just as meaningful on a testnet.

Front end is a **Next.js web UI with a CIP-30 wallet** (chosen). Architecture (same shape as the
xUSDC webapp): a **Java/Spring backend** builds the posting transaction and serves the feed; the
**browser wallet** (Lace/Eternl) signs and submits. The on-chain core is the same whatever the UI.

Testnet-first, mainnet-portable: network + backend URL come from config.

## 2. Locked decisions
| # | Decision | Why |
|---|----------|-----|
| D1 | Front end = **Next.js + CIP-30 wallet**; off-chain = **Java/Spring backend** | authentic web wall; keeps tx-building in Java (house style); the wallet signs |
| D2 | Backend **builds an unsigned tx**; the wallet signs + submits | user keeps custody of keys; no private keys on the server |
| D3 | Start **metadata-only** (no contract) | simplest on-chain write; a working wall fast |
| D4 | Messages are **chunked to 64-byte** text values | Cardano metadata string limit |
| D5 | UI uses the **raw CIP-30 API** (no Mesh/Lucid) since the backend builds the tx | minimal UI dependencies |
| D6 | dApp/datum + NFT receipt + fee + moderation are **optional extensions** | the post+feed+UI core ships first |
| D7 | testnet-first, network = config | mainnet is a config change |

## 3. Tech stack (allowed list)
- Backend: Java 21 + Gradle (wrapper 8.10.2) + Spring Boot (web) + bloxbean cardano-client-lib +
  backend-blockfrost; JUnit 5 + AssertJ + Spotless.
- Frontend (`ui/`): Next.js + TypeScript + React, talking to a CIP-30 wallet via `window.cardano`
  (no Cardano JS lib needed - the backend builds the tx; the wallet just signs + submits).
- Aiken only if/when we add the optional dApp/datum version.
- Integration tests `@Tag("integration")` self-skip without a backend.

## 4. Repo layout
```
memory-wall/
  CLAUDE.md / PROGRESS.md / build.gradle.kts / settings.gradle.kts / gradlew + gradle/wrapper
  src/main/java/org/wall/   # WallPost, Wall (metadata + tx build), Spring API, feed reader
  src/test/java/org/wall/
  ui/                       # Next.js + TypeScript front end (added in Ch 04)
  chapters/NN-title/README.md
  infra/                    # devnet/testnet runbook (later)
```

## 5. Roadmap (chapters)
- 00 Orientation - the wall; the backend-builds / wallet-signs architecture; how the course works.
- 01 Post a message (Java core) - WallPost + chunk to 64 bytes + build metadata (TDD).
- 02 Read the feed (Java core) - parse posts back; list newest-first (fetch is integration).
- 03 Backend API (Spring Boot) - POST build-an-unsigned-post-tx + GET feed (MockMvc tests).
- 04 Web UI (Next.js + CIP-30 wallet) - connect wallet, post (backend builds, wallet signs+submits),
  render the live feed. Verified by typecheck/build.
- 05 Testnet + wrap-up - preprod config, mainnet notes, optional extensions (dApp/datum, NFT receipt,
  fee + pin, curator moderation), what we simplified.

## 6. Going to mainnet
Switch the backend URL + network in config; posting/reading logic and the UI are identical. Users
already hold their own keys (the wallet signs), so no server-side key handling changes.

## 7. How to continue right now
Read `PROGRESS.md` -> Current chapter + Next steps -> TDD -> keep it green
(`./gradlew spotlessApply test`) -> update `PROGRESS.md` -> commit + tag.
