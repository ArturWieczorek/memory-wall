# Changelog

All notable changes to this project are documented here. The format is based on Keep a Changelog,
and this project uses semantic-version releases alongside per-chapter teaching tags (`ch00`..`ch15`).

## [1.0.0] - 2026-07-15

First tagged release: a complete, tested, and publicly hosted message wall.

### Added
- **Core wall (ch00-ch05).** Post a short message recorded permanently as Cardano transaction
  metadata (label 1719, message chunked to 64-byte values); read it back as a live, newest-first
  feed. Spring Boot backend builds an UNSIGNED transaction; a Next.js + CIP-30 browser wallet signs
  and the backend submits. The server holds no keys and needs no funds. Testnet-first, network from
  config.
- **Serve it from home (ch06).** `/api/health` + a UI status light, CORS, per-IP rate limiting,
  display-side blocklist moderation, a Blockfrost read-only fallback, runtime-configurable backend
  URL, and localhost binding. A beginner networking + home-hosting chapter.
- **UX polish (ch07).** Dark/light theme, relative timestamps, byte counter, block-explorer links,
  network label, friendly empty state; first UI test suite (Vitest + React Testing Library).
- **Verified author identity (ch08).** The payer address is read from the transaction and shown as a
  verified chip next to the claimed display name.
- **CI + free GitHub security (ch09).** CI on push/PR, Dependabot, CodeQL, secret scanning + push
  protection, MIT license.
- **Search + precise moderation (ch10).** Feed search plus an exact tx-hash "hide this post" lever.
- **Fee + pin tier (ch11).** Optional, off by default: tip to post, tip more to pin. Scarce,
  competitive, time-limited pins, verified on-chain, stateless (no queue).
- **Pin colour palette (ch12).** The payer picks a pastel from a fixed palette, stored on-chain and
  safe-validated on write and read.
- **Pagination (ch13).** "Load more" over paged feed reads.
- **Indexer (ch14).** In-memory cache of all posts (incremental refresh) enabling full-history
  `/api/search` and global pin ordering.
- **Durable index (ch15).** Optional SQLite store behind a `PostStore` seam; set `WALL_INDEX_DB_PATH`
  to make the index survive restarts (warm start). In-memory remains the default.
- **Repo hygiene.** JaCoCo coverage, UI ESLint + Prettier (wired into CI), self-skipping live-chain
  integration tests, consolidated `AGENT.md`, and governance files (Contributing, Security, Code of
  Conduct).

### Hosting
- Live behind a stable Cloudflare named tunnel on the operator's own domain, with free Cloudflare edge
  hardening; the UI auto-deploys to GitHub Pages.

### Deferred / out of scope
- Image posts + admin approval queue: deferred; designed and threat-analysed (SSRF, CSAM/legal) in
  `docs/`. NFT receipt and dApp/datum: out of scope (better taught by other portfolio projects).

[1.0.0]: https://github.com/ArturWieczorek/memory-wall/releases/tag/v1.0.0
