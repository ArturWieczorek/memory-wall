# Memory Wall - backlog and feature roadmap

The single canonical list of everything proposed for the wall: what is planned, what is being built,
what shipped, and what was deliberately left out (with the reason). Updated as work lands.

> This supersedes the scattered "optional/next steps" notes. `PROGRESS.md` points here.
> Every feature (or coherent set) gets its own chapter under `chapters/NN-title/README.md` - a
> beginner tutorial (concept + analogy -> what we build -> tests first -> step by step -> pitfalls ->
> glossary) - and tests (JUnit for the backend, Vitest + React Testing Library for the UI).

## Status legend
- `[ ]` planned  -  `[~]` in progress  -  `[x]` done (chapter + tag)  -  `[out]` out of scope (see why)

## Now - selected for build (chapters 07-09)

These are the three areas chosen on 2026-07-13 (tiers B, C, and D-minus-systemd from the review).

### Chapter 07 - Polish the wall (UX)  `[~]`
| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 07.1 | Block-explorer link per post | `[ ]` | Needs the tx hash end to end (backend `WallPost.txHash` -> feed JSON -> UI links to cardanoscan for the wall's network). |
| 07.2 | Show the wall's network in the header | `[ ]` | So visitors set their wallet to the right network (preprod). |
| 07.3 | Message byte counter | `[ ]` | UTF-8 bytes vs the backend cap (`wall.max-message-bytes`, 4096). |
| 07.4 | Relative timestamps ("2h ago") | `[ ]` | Pure function, unit-tested. |
| 07.5 | Friendlier empty state | `[ ]` | "No posts yet - be the first." |
| 07.6 | UI test setup (Vitest + RTL) | `[ ]` | New dev dependency; enables TDD for all UI work. Logged in PROGRESS. |

### Chapter 08 - Who really posted this? (verified author identity)  `[ ]`
| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 08.1 | Surface the signing wallet address | `[ ]` | The free-text author is spoofable; show the actual payment/stake address as the verifiable identity. |
| 08.2 | "verified vs claimed" display | `[ ]` | Distinguish the on-chain address from the free-text name in the feed. |

### Chapter 09 - Keep it healthy (CI + free GitHub security)  `[ ]`
| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 09.1 | CI workflow | `[ ]` | Run `gradle test` + spotless + `next build`/typecheck on push/PR (separate from the Pages deploy). |
| 09.2 | Dependabot | `[ ]` | `.github/dependabot.yml` for Gradle + npm + GitHub Actions version updates. |
| 09.3 | CodeQL code scanning | `[ ]` | `.github/workflows/codeql.yml` (free on public repos). |
| 09.4 | Secret scanning + push protection | `[ ]` | Enable via repo settings (free on public repos). |
| 09.5 | Bump Action versions | `[ ]` | Clears the Node 20 deprecation warning in the deploy workflow. |
| 09.6 | LICENSE | `[ ]` | Public portfolio repo should carry one. |
| 09.7 | Per-visitor rate limit note | `[ ]` | Doc `WALL_CLIENT_IP_HEADER=CF-Connecting-IP` behind Cloudflare. |

## Later - documented, not scheduled

| Feature | Status | Notes |
|---------|--------|-------|
| Client-side search/filter | `[ ]` | Trivial filter over the loaded feed (author/message substring). Cheap win; only covers the recent window. |
| Full-history search | `[ ]` | Needs an indexer/cache (pairs with pagination). Provider by-label endpoint has no text search. |
| Pagination / "load more" | `[ ]` | The feed is one label query (recent ~20). A large wall needs pagination + an indexer. |
| Fee + pin tier | `[ ]` | Require a post to pay >= X to a fee address; higher fee pins it. Backend-enforceable when building (no contract needed). Spam economics for a busy wall. |
| Tx-hash curator moderation | `[ ]` | Hide by tx hash (today's blocklist matches author/message terms only). Small extension of `Blocklist`. |
| Stable public hosting | `[~]` | Quick Cloudflare tunnel working; Tailscale Funnel for a stable URL is the next hosting step (`infra/HOSTING.md`). |

## Deferred - designed, held back on purpose

| Feature | Status | Why held |
|---------|--------|----------|
| Image posts + admin approval queue | `[ ]` | Fully designed in `docs/future-images.md`. Real weight: SSRF (server fetching attacker URLs) and legal duties (CSAM). The only genuinely wall-relevant "big" feature, but it needs its own careful chapter. |

## Out of scope for Memory Wall

Kept off the list on purpose - they do not serve the wall's goal (post + read messages) and have
better homes in the portfolio.

| Feature | Status | Why not here |
|---------|--------|--------------|
| NFT receipt (CIP-25) | `[out]` | Minting a token per post is a gimmick here (adds cost + complexity, no functional benefit). CIP-25 is taught with real purpose by the portfolio's NFT-ticketing project. |
| dApp / datum storage | `[out]` | Metadata already does the wall's job (permanent, readable, cheap). Datum/contract only pays off for on-chain rules the wall does not need; it locks min-ADA per post. Validators are taught with purpose by the vesting / crowdfunding / auction / voting projects. |
