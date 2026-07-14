# Memory Wall - backlog and feature roadmap

The single canonical list of everything proposed for the wall: what is planned, what is being built,
what shipped, and what was deliberately left out (with the reason). Updated as work lands.

> This supersedes the scattered "optional/next steps" notes. `AGENT.md` points here.
> Every feature (or coherent set) gets its own chapter under `chapters/NN-title/README.md` - a
> beginner tutorial (concept + analogy -> what we build -> tests first -> step by step -> pitfalls ->
> glossary) - and tests (JUnit for the backend, Vitest + React Testing Library for the UI).

## Status legend
- `[ ]` planned  -  `[~]` in progress  -  `[x]` done (chapter + tag)  -  `[out]` out of scope (see why)

## Now - selected for build (chapters 07-09)

These are the three areas chosen on 2026-07-13 (tiers B, C, and D-minus-systemd from the review).

### Chapter 07 - Polish the wall (UX)  `[x]` (tag ch07)
| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 07.1 | Block-explorer link per post | `[x]` | Tx hash carried end to end (`WallPost.txHash` -> feed JSON -> UI "view tx" link via `explorerTxUrl`). |
| 07.2 | Show the wall's network in the header | `[x]` | `network: <net>` chip in the header. |
| 07.3 | Message byte counter | `[x]` | `123 / 4096 bytes`, turns red + disables Post over the cap. |
| 07.4 | Relative timestamps ("2h ago") | `[x]` | `relativeTime()` pure fn, unit-tested; exact time on hover. |
| 07.5 | Friendlier empty state | `[x]` | Online: "be the first"; offline: hint to paste a Blockfrost key. |
| 07.6 | UI test setup (Vitest + RTL) | `[x]` | Added Vitest + RTL + jsdom; `npm test`. 19 UI tests (lib + FeedList). |
| 07.7 | Dark/light theme toggle | `[x]` | CSS variables + `data-theme`; no-flash init in layout; logic unit-tested. |

### Chapter 08 - Who really posted this? (verified author identity)  `[x]` (tag ch08)
| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 08.1 | Surface the signing wallet address | `[x]` | Backend reads the payer address from the tx's first input (verifiable, not spoofable); flows to feed JSON. `WallPost.address`. |
| 08.2 | "verified vs claimed" display | `[x]` | Feed shows name + `(claimed)` + a `verified` short-address chip linking to cardanoscan (full address on hover). `shortenAddress`, `explorerAddrUrl`. |
| 08.3 | Privacy trade-off + cost documented | `[x]` | Chapter covers linking-to-a-funded-wallet and the N+1 lookup (indexer fixes it). Best-effort: failure -> no chip, feed still works. |

### Chapter 09 - Keep it healthy (CI + free GitHub security)  `[x]` (tag ch09)
| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 09.1 | CI workflow | `[x]` | `.github/workflows/ci.yml` - backend (spotlessCheck+test) + UI (typecheck+test+build) on push/PR. |
| 09.2 | Dependabot | `[x]` | `.github/dependabot.yml` - gradle + npm(ui) + github-actions, weekly. |
| 09.3 | CodeQL code scanning | `[x]` | `.github/workflows/codeql.yml` - java-kotlin (autobuild) + javascript-typescript. |
| 09.4 | Secret scanning + push protection | `[x]` | Enabled via settings (+ Dependabot alerts + security-fix PRs). |
| 09.5 | Bump Action versions | `[x]` | deploy-ui.yml bumped to current majors (checkout v7, setup-node v6, configure-pages v6, upload-pages-artifact v5, deploy-pages v5); clears Node 20 warning. |
| 09.6 | LICENSE | `[x]` | MIT (2026 Artur Wieczorek). |
| 09.7 | Per-visitor rate limit note | `[x]` | Documented in the chapter + infra/HOSTING.md (`WALL_CLIENT_IP_HEADER=CF-Connecting-IP` behind Cloudflare). |

## Later - documented, not scheduled

| Feature | Status | Notes |
|---------|--------|-------|
| Client-side search/filter | `[x]` (ch10) | `filterPosts` over the loaded feed (author/message substring); search box + "N of M" hint + no-match state. Recent window only (labelled as such in the UI). |
| Full-history search + global pinning | `[x]` (ch14) | In-memory `WallIndex` ingests all posts (incremental refresh); `/api/search` searches every post and the feed pins/orders globally. |
| Persistent index store (survive restarts) | `[x]` (ch15) | Optional SQLite store behind a `PostStore` seam (default = in-memory no-op, unchanged). Set `wall.index.db-path` to persist; the index seeds from it on startup (warm start) and saves only new posts. One dependency (`sqlite-jdbc`), inert unless enabled. Roundtrip + seed/save unit-tested. |
| Pagination / "load more" | `[x]` (ch13) | Feed reader + `/api/feed` take a `page`; UI "Load more" appends the next page (de-duped by tx hash), hidden on a short page. Pins/search still act on the loaded window (full-history needs the indexer). |
| Pin colour (payer palette) | `[x]` (ch12) | Poster picks a pastel from a fixed 6-colour palette when pinning; stored on-chain (`c`), safe-validated on write + read, rendered behind the pin. Emerged from the Ch11 design chat. |
| Fee + pin tier | `[x]` (ch11) | Optional tier (off by default). Tip >= min to post, >= pin-fee to pin. Scarce slots (max-pinned), competitive (highest tip, can bump), time-limited (pin-duration, default 7d), verified on-chain. Stateless (no queue). Default pastel for pins; payer palette = ch12. |
| Tx-hash curator moderation | `[x]` (ch10) | `wall.blocked-tx-hashes` hides an exact post by tx hash (scalpel), alongside the term blocklist (broad brush). Display-side only. |
| Stable public hosting | `[x]` | Live on a stable Cloudflare **named** tunnel (`wall.arturwieczorek.com`) on the operator's own domain + free Cloudflare edge hardening (rate limit, Bot Fight, DDoS). Full runbook in `infra/HOSTING.md`; hardening in `infra/CLOUDFLARE-HARDENING.md`. |

## Deferred - designed, held back on purpose

| Feature | Status | Why held |
|---------|--------|----------|
| Image posts + admin approval queue | `[ ]` | Designed in `docs/future-images.md`; threat-analysed in `docs/IMAGE-POSTS-THREAT-ANALYSIS.md`. Real weight: SSRF (server fetching attacker URLs) and legal duties (CSAM). Safe path if built: link-only + `imgHash`-pinned + default-deny + tailnet-bound admin + click-to-load, NO server fetch/rehost/ML, testnet-first, needs Terms + legal posture. The only genuinely wall-relevant "big" feature, but it needs its own careful chapter. |

## Out of scope for Memory Wall

Kept off the list on purpose - they do not serve the wall's goal (post + read messages) and have
better homes in the portfolio.

| Feature | Status | Why not here |
|---------|--------|--------------|
| NFT receipt (CIP-25) | `[out]` | Minting a token per post is a gimmick here (adds cost + complexity, no functional benefit). CIP-25 is taught with real purpose by the portfolio's NFT-ticketing project. |
| dApp / datum storage | `[out]` | Metadata already does the wall's job (permanent, readable, cheap). Datum/contract only pays off for on-chain rules the wall does not need; it locks min-ADA per post. Validators are taught with purpose by the vesting / crowdfunding / auction / voting projects. |
