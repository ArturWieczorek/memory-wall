# Threat / security analysis: image posts + admin approval queue

> Status: the image feature is DEFERRED (see docs/BACKLOG.md). This document is the security review
> that must be satisfied before it is built. Design sketch: docs/future-images.md. Not legal advice -
> confirm duties for your jurisdiction with counsel before hosting user images publicly.

## Executive summary

The image feature is the single most dangerous thing this project could add, and the danger is
almost entirely determined by **one decision the current design leaves ambiguous: does the *server*
ever fetch / decode / store the image bytes, or does only the *viewer's browser* ever touch them?**

- `docs/future-images.md` mostly describes a **client-render, link-only** model (browser `<img>`,
  click-to-load, in-browser hashing) - but also proposes **server-side NSFW/CSAM classification**,
  which requires the server to fetch and decode attacker-supplied bytes. Those two halves have
  opposite risk profiles and cannot both be "safe-by-default."
- Recommendation: an MVP that is **link-only, default-deny (admin-approved), click-to-load, with NO
  server-side fetching, NO rehosting, and NO ML classifiers.** That eliminates SSRF, decoder
  exploits, decompression bombs, and (critically) the operator ever *possessing* the bytes. Anything
  that needs the server to touch bytes is refused for the MVP and revisited only with legal counsel.
- Honest hard limit: **you cannot moderate a permanent, permissionless chain.** Approval only
  controls *your* feed. The on-chain URL is public forever, and the project's own offline Blockfrost
  fallback in the browser (`ui/app/page.tsx`) reads raw metadata directly, bypassing any backend
  approval gate. Any image feature must be designed knowing the gate is advisory, not absolute.

## Current architecture the feature must fit into

- Backend builds an **unsigned** tx; the wallet signs+submits. **No server keys** (D2). Posts are tx
  metadata under **label 1719**, shape `{a, m, ts, c?}` (`Wall.java`).
- Feed is served from an in-memory `WallIndex` (ch14; ch15 adds an optional SQLite store). Moderation
  today is **display-side, opt-out**: `Blocklist` (terms + exact tx-hash), applied when serving the
  feed. It cannot erase anything on-chain.
- **No authentication anywhere.** No cookies/sessions (CORS `*` is safe precisely because there are
  no credentials). The API is stateless; the fee/pin tier was deliberately stateless, no queue.
- Server binds to `127.0.0.1`, reachable only via a Cloudflare named tunnel; per-IP rate limit uses a
  trusted header or the socket address (`RateLimitFilter`).
- UI is a Next.js static export on GitHub Pages. React escapes text by default; there is no
  `dangerouslySetInnerHTML` anywhere. An offline fallback fetches raw label-1719 metadata straight
  from Blockfrost in the viewer's browser with a viewer-supplied key.

The feature adds three things this codebase has never had: **mutable server-side state** (approval
status), **an authenticated surface** (admin), and **rendering of attacker-controlled remote
content**.

## The pivotal decision: where do the image bytes flow?

| Model | Who fetches bytes | SSRF | Decoder/bomb exposure | Operator "possesses" bytes (CSAM weight) | Real moderation (strip EXIF, etc) | Viewer IP leak | House-style fit |
|---|---|---|---|---|---|---|---|
| M1 Link-only, client renders (click-to-load) | Viewer browser only | None (server) | None (server) | No | No | Yes, on click | Good (no deps, no keys) |
| M2 Operator proxy / rehost to object store | Server, then serves bytes | High | High | Yes (maximum) | Yes | No (proxied) | Poor (storage creds, heavy) |
| M3 IPFS + pin | Gateway / your pin node | Med (if you pin) | Med | Yes if you pin | No | Leaks to gateway | Medium |
| M2b Server fetch only to classify | Server (transient) | High | High | Yes (transient possession still counts) | N/A | No | Poor (ML deps) |

The design's render path is M1; its "assist the queue" path is M2b. **M2b silently converts the safe
M1 design into the dangerous one.** This is the key finding.

## Threat analysis

### 1. SSRF (server fetching attacker-supplied URLs)
Only exists in M2/M2b/M3-pin. If the backend fetches a poster-controlled URL, an attacker points it
at internal targets. On this home-hosted box the internal surface is real: `localhost:8090` (the
backend itself), the local Blockfrost/Yaci provider (`http://localhost:8080`), the router
(`192.168.x`), cloud/link-local metadata (`169.254.169.254`), and the whole home LAN - all behind the
firewall the tunnel is meant to protect.
- Likelihood: High if any server fetch exists (trivial to trigger). Impact: High (internal recon,
  hitting the unauthenticated local provider, DNS-rebinding to reach `127.0.0.1`).
- Mitigations: *preferred - do not fetch server-side at all (M1).* If unavoidable: https-only scheme
  allowlist; resolve DNS and reject private/loopback/link-local/reserved/CGNAT ranges; connect to the
  resolved IP and pin it (defeat DNS rebinding / TOCTOU); do not follow redirects (or re-validate each
  hop); hard timeouts; response-size cap; run the fetcher in an egress-restricted sandbox that can
  only reach the public internet, never the LAN or localhost.

### 2. Illegal content / CSAM + operator legal duties (the genuinely hard part)
This is the reason the feature is deferred, and it deserves blunt treatment. **Not legal advice.**
- A public wall that renders arbitrary user images *will* eventually be pointed at illegal content,
  including CSAM. On a permanent, permissionless chain you cannot delete the pointer - only hide it
  from your feed.
- Section 230 (US) shields platforms from liability for third-party *content*, but expressly does not
  cover federal criminal law - and CSAM is federal criminal law. DMCA section 512 safe harbor is for
  *copyright* only and requires a registered agent + a takedown process.
- CSAM reporting (US, 18 U.S.C. 2258A): no duty to proactively monitor, but once a provider obtains
  actual knowledge of apparent CSAM it must report to NCMEC and preserve the material for the
  statutory window - and must not redistribute it. **Server-side fetching to classify (M2b) means you
  download the bytes -> transient possession -> you are now in the reporting/preservation regime**,
  and mishandling is itself a crime. EU DSA adds notice-and-action obligations and CSAM-specific rules.
- Net: M1 (link-only, you never hold the bytes, you act on reports, you have a takedown/hide path) is
  the defensible "display intermediary" posture. M2/M2b make you a host/possessor and multiply the
  legal weight. Bottom line: *if you are not prepared to receive a CSAM report and handle it correctly
  (report to NCMEC, preserve, do not redistribute), do not enable public image posting on mainnet.*
- Likelihood: Medium-High over time. Impact: Catastrophic (criminal, not just civil).
- Mitigations: default-deny approval; link-only (M1); a working report button + prompt hide (extend
  `Blocklist`); Terms of Use; register a DMCA agent if hosting publicly; you are already on Cloudflare
  (its free CSAM Scanning Tool runs at the edge, so you never decode bytes yourself - the one
  classifier worth wiring); keep images testnet/demo-only per the testnet-first house style.

### 3. Storage model abuse / cost / permanence
On-chain bytes are impossible (64-byte value cap, ~16KB tx). A plain HTTPS link is a mutable pointer
(link-rot + swap-after-approval). An IPFS CID is tamper-evident but vanishes unless pinned (pinning =
hosting weight). An operator object store gives full control but adds storage cost + credentials +
host liability. An `imgHash`/CID pins approval to specific bytes and defeats the swap attack.
- Likelihood (link swap): Medium. Impact: High (approve benign, poster swaps to illegal).
- Mitigation: store `imgHash` on-chain, gate approval on the hash, verify in-browser at render, refuse
  to render on mismatch; prefer IPFS CID (the hash *is* the reference).

### 4. Image parsing / decoder vulnerabilities and malware
Only relevant if the server decodes (M2b) or the browser is tricked into executing.
- SVG XSS: SVG can carry `<script>`; rendered as a document it executes. Refuse SVG - raster allowlist
  only (png/jpeg/gif/webp).
- Polyglots: a file valid as both image and HTML/JS. Harmless in an `<img>` tag, dangerous if ever
  served same-origin and navigated to. Never host user bytes on the app's own origin; if proxying,
  serve from a separate origin with `Content-Disposition: attachment` + strict CSP.
- Decompression / zip bombs: a tiny file that decodes to gigapixels -> OOM. Cap file size before
  decode, cap dimensions/total pixels, time out. (Only bites M2b.)
- Decoder CVEs: feeding untrusted bytes to an image library is a classic RCE vector. Avoid decoding in
  the MVP; if unavoidable, sandbox it.
- EXIF/GPS privacy: a poster's own images may leak GPS coordinates / camera serials. In M1 you cannot
  strip them (the user hosts) - you can only warn posters.
- Likelihood: Medium (only if the server decodes). Impact: High (RCE/DoS).

### 5. Content-type sniffing
The URL extension / `Content-Type` cannot be trusted, and you cannot know the content-type before
fetching - so a client `<img>` may receive an SVG or HTML. Mitigations: rely on `<img>` (which will
not execute HTML/most SVG script); set `X-Content-Type-Options: nosniff` on anything you ever serve;
if proxying, validate real magic bytes and re-encode to a canonical raster; strict CSP (`img-src`
allowlist, `script-src 'self'`) on both the public and admin pages.

### 6. Denial-of-service / cost amplification
Posting is cheap and permissionless. An attacker floods image posts. Amplification only exists if each
post triggers a server fetch/decode/classify (M2b) - then one cheap on-chain post costs you bandwidth
+ CPU. Queue flooding also bloats the index and the pending queue.
- Likelihood: Medium. Impact: Medium.
- Mitigations: default-deny means flooding never reaches viewers (only bloats the queue); no server
  fetch removes amplification; cap concurrent fetches + size + per-IP rate (existing
  `RateLimitFilter`); consider gating image posts behind the existing fee/pin tier; bound the index /
  paginate the queue.

### 7. Admin approval queue - its own attack surface
This is the project's first authenticated, stateful surface, and it renders attacker-controlled data
to a privileged user.
- AuthN/AuthZ: prefer a network split - bind admin routes to the Tailscale tailnet interface only,
  never funneled through the public Cloudflare tunnel. Safest option, needs no password. If a token is
  used instead: from env only, constant-time compare, never logged, HTTPS-only, fail closed. Ensure
  the *public* feed endpoints can never mutate status.
- CSRF: keep the API token/header-based and cookie-free (as today) -> CSRF does not apply. If anyone
  later adds a session cookie, approve/reject becomes CSRF-able; forbid that. Approve/reject must be
  POST, never GET (no drive-by via an `<img>`/link in the queue itself).
- Stored XSS in the admin view (high severity): the queue renders attacker-controlled `author`,
  `message`, and the image URL. If the admin UI ever uses `innerHTML`, or puts the URL into an
  `href`/attribute without scheme-checking, an attacker gets script execution in the authenticated
  admin context -> full compromise of the one privileged surface. Mitigations: React escaping only (no
  `dangerouslySetInnerHTML`), reject `javascript:`/`data:` schemes - allow only `https://` and
  `ipfs://`, strict CSP on the admin page, and do not auto-load the image in the admin preview (the
  admin's browser fetching the attacker URL leaks the admin's IP and exposes them to exploits) - use
  click-to-load in the admin too.
- Queue poisoning/flooding: see item 6; default-deny + auth + pagination contain it.
- Likelihood: Medium. Impact: High (admin compromise).

### 8. Moderation bypass / permanence
The gate is advisory: (a) the on-chain URL is public forever; (b) the UI's own offline Blockfrost
fallback reads raw metadata client-side and would surface `img` regardless of approval status; (c)
mutable URLs allow swap-after-approval. Mitigations: treat approval as "what *our* feed renders,"
documented honestly; make the offline fallback ignore/strip `img` (only the approved backend feed may
emit it); pin approval to `imgHash`/CID; keep the report->hide path. Accept that a determined chain
reader is out of your control - inherent to a permanent permissionless wall.

### 9. Privacy / deanonymization
Auto-loading `<img src>` turns every image into a tracking pixel: the poster's host learns each
viewer's IP, UA, and referrer. IPFS public gateways leak to the gateway operator. Poster EXIF leaks
the poster's location. Poster address is already linked on-chain (ch08).
- Likelihood: High if auto-load. Impact: Medium.
- Mitigations: click-to-load (viewer opts in), `referrerpolicy="no-referrer"`, `crossorigin`,
  `loading="lazy"`, no `<img>` until clicked; warn posters about EXIF; document the trade-off.

## Recommended phased plan

### Phase 0 - prerequisites (before any code)
Terms of Use + a report path; confirm jurisdiction/legal posture; decide testnet/demo-only vs mainnet
(recommend testnet-only for this teaching project). Register a DMCA agent if it will ever host
publicly. Add a report button that feeds the existing `Blocklist` hide path.

### Phase 1 - safe-by-default MVP (build this or nothing)
- Metadata: optional `img` (https/ipfs URL, chunked like `m`) + `imgHash` (sha256); forward-compatible
  with `{a,m,ts,c?}`. Validate scheme allowlist (https/ipfs only; reject javascript:/data:) and length
  on write (`WallController.build`) and re-validate on read (`Wall`/`BlockfrostFeedReader.tryParse`),
  exactly as `PinColors.normalize` already defends the `c` field.
- Default-deny approval queue: a small SQLite store mapping `txHash -> pending|approved|rejected`
  (ch15's PostStore/SQLite groundwork is the natural home). Feed emits `img` only for approved tx;
  otherwise an "image pending review" placeholder (text still shows).
- Admin surface: separate routes, bound to the Tailscale tailnet only, never funneled. List pending,
  approve/reject via POST. No cookies. Admin preview is click-to-load, not auto-load.
- Rendering: client-side `<img>` only, click-to-load, `referrerpolicy=no-referrer`, `onerror`
  placeholder, in-browser SHA-256 verify against `imgHash` (render only on match). No server fetch.
  Strict CSP on both pages.
- Offline fallback: make the chain-read path ignore `img` so it cannot bypass the gate.
- NO server-side fetching, NO rehosting, NO ML classifiers, NO IPFS pinning in Phase 1.

### Phase 2 - only with legal sign-off (probably out of scope for this project)
Edge CSAM scanning via Cloudflare's free tool (runs at the edge, you never decode). If - and only if -
real pre-display moderation is required: server-side fetch in an egress-restricted sandbox with the
full SSRF guard set (item 1), size/pixel caps, magic-byte validation + nosniff, re-encode to canonical
raster, EXIF strip, hash-pin. Large, dependency-heavy, materially raises legal exposure; document as
"requires counsel," do not ship casually.

## Explicit "do NOT build / out of scope"
- No server-side image fetching of poster URLs (SSRF + possession).
- No operator rehosting / object store (storage creds violate "no server-side keys"; makes you a host).
- No self-hosted NSFW/CSAM ML classifiers - heavy deps, false pos/neg, and CSAM classification means
  decoding bytes = possession. Defer to Cloudflare's edge tool only.
- No SVG / no `data:` / no `javascript:` URLs - raster allowlist only.
- No auto-loading remote images - click-to-load always.
- No cookie/session auth for admin - keep it cookie-free (token or, preferably, tailnet-bound).
- No IPFS pinning by the operator unless you accept host duties.
- No on-chain image bytes - physically impossible and an abuse magnet.

## Conflicts with the house style
- Minimal dependencies: the approval queue forces the project's first persistent store (ch15 already
  laid the SQLite groundwork) and first auth - keep it tiny, no ML libs.
- No server-side keys (D2): honored by M1; violated by rehosting (needs object-store creds).
- Stateless design: the fee/pin tier was deliberately stateless/no-queue; the approval queue is a real
  architectural shift (mutable state + auth) - acknowledge it explicitly in the chapter.
- Backend-builds / wallet-signs (D1/D2): unaffected - `img` is just another metadata field.
- Testnet-first (D7): aligns with the legal recommendation - keep images demo/testnet to cap exposure.

## Bottom line
Ship images only as: link-only, `imgHash`-pinned, default-deny with a tailnet-bound admin queue,
click-to-load, no server fetch, no ML, testnet-first, with a report/takedown path and Terms. Refuse
everything that makes the server touch the bytes. And state plainly in the chapter that a permanent
permissionless wall cannot truly moderate images - the approval gate governs your feed, not the chain.

## Critical files if/when implemented
- `src/main/java/org/wall/Wall.java` - add + safe-validate the optional `img`/`imgHash` fields.
- `src/main/java/org/wall/WallController.java` - write-side validation, approval-gated feed, new
  tailnet-bound admin endpoints.
- `src/main/java/org/wall/Blocklist.java` - extend into the report/hide + approval-status seam.
- `src/main/java/org/wall/BlockfrostFeedReader.java` - parse/validate `img` on read; NEVER fetch bytes.
- `ui/app/page.tsx` - click-to-load rendering, in-browser hash verify, and close the offline-Blockfrost
  `img` bypass.
