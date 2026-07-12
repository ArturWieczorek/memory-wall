# Future extension: image posts (design advice, NOT built yet)

The wall is text-only today. This note captures how images *should* be added later, and the legal
shape, so the decision is not re-derived from scratch. Nothing here is implemented; the current
metadata format is kept forward-compatible (an optional field can be added without breaking old
posts).

## Why images cannot go on-chain directly
Cardano transaction metadata caps each text value at 64 bytes, and a whole transaction is ~16 KB.
Even a small image is tens to hundreds of KB, so it physically cannot be stored in the metadata. The
only workable model is: store the image OFF-CHAIN, and put a short LINK on-chain.

## Recommended model: user hosts, we store a link, we render, we degrade gracefully
1. The user hosts the image themselves and supplies a link:
   - a plain **HTTPS URL**, or
   - an **IPFS** reference (`ipfs://<CID>`, rendered via a public gateway).
2. On-chain we store only that short link (fits the 64-byte limit, or chunk it like the message).
   Optionally also store the image's **content hash** for integrity.
3. The feed renders `<img src=...>`. An `onerror` handler swaps in an "image unavailable" placeholder
   if the URL 404s or the gateway is down - so a dead link shows a clean message, not a broken page.

### Tradeoffs to decide between
- **Plain HTTPS link** - simplest, but it is only a *pointer*: the host can change, remove, or swap
  the file later. The link is permanent on-chain, but what it points to is mutable (link rot +
  swap risk). The on-chain record does not guarantee what a viewer sees.
- **IPFS (`ipfs://<CID>`)** - the CID is the hash of the content, so the reference is tamper-proof
  (anything served for that CID is guaranteed to be the original). BUT it must be **pinned** by
  someone or it disappears. Integrity yes; availability only if pinned.
- **Link + on-chain hash** - keep an HTTPS link but also store the image's hash; the feed can verify
  the fetched bytes match the hash, catching a swapped file. Best integrity with a normal URL.

Recommendation: prefer **IPFS** (or **link + hash**) if tamper-evidence matters; a plain HTTPS link
is acceptable if you accept that it is just a mutable pointer.

## Integrity: hashing the image content
To make even a plain HTTPS link tamper-evident, store the image's **content hash** on-chain next to
the link (e.g. `{ "img": "https://...", "imgHash": "<sha256 hex>" }`), and on display:
1. fetch the bytes from the link,
2. hash them in the browser (Web Crypto SHA-256 - the same technique the proof-of-existence project
   uses),
3. compare to the on-chain `imgHash`.

- **Match** -> render the image; you have cryptographic proof it is exactly what the poster
  committed to, even though it is served from a mutable URL.
- **Mismatch** -> do NOT render; show "image changed since it was posted" (the host swapped the file,
  or it was corrupted/replaced).

This gives IPFS-grade integrity with an ordinary URL. (With IPFS the CID already *is* the hash, so a
separate `imgHash` is redundant there.) The hash is small (64 hex chars = 64 bytes) and fits the
metadata limit as a single value.

## Safe rendering: click-to-load, not auto-load
Do NOT auto-fetch/auto-render remote images that strangers posted. Instead show a placeholder with a
"Load image" button; only fetch when the viewer clicks. Reasons:
- **Privacy / IP leak:** auto-loading `<img src="http://attacker/...">` sends every viewer's IP,
  user-agent, and referrer to an arbitrary host chosen by the poster (a tracking/deanonymisation
  vector). Click-to-load makes that the viewer's explicit choice.
- **Safety:** you avoid your page automatically pulling in unknown/malicious/huge content; the
  viewer opts in per image.
- **Legal exposure:** you are not automatically displaying unvetted content to everyone; rendering
  becomes a deliberate, per-item action, which pairs well with the report/hide flow.
- **Bandwidth / broken links:** nothing is fetched until wanted; a dead link just never loads (with
  an `onerror` "image unavailable" message if the viewer does click).

Combine with: the same **blocklist** applied to text (so a reported image link can be hidden), and an
`onerror` handler for dead/removed links.

## Legal / responsibility (general shape - NOT legal advice; confirm for your jurisdiction)
- **The user is the publisher.** They sign and submit their own transaction carrying the link, under
  their own key, and they host the file. That is classic user-generated content (UGC).
- **Your site displays/renders it**, which is where intermediary/host duties attach. Most
  jurisdictions have safe-harbor / intermediary protections (e.g. US CDA 230 / DMCA 512, EU DSA
  hosting exemption) that shield a platform from what users post **provided** it offers a way to
  report content and **removes it from display on valid notice**.
- **The decentralization twist:** you cannot delete anything from the chain (the link is permanent),
  but you can **hide it from your feed**. That display-side control is both your lever and your
  responsibility surface - and it is exactly the **blocklist / moderation hook** already in the
  backend (`Blocklist`). Images raise the stakes over text because your site actively fetches and
  renders a remote file.
- If/when images are added, pair them with: terms of use, a "report this post" button, prompt
  hide-on-notice (via the blocklist), and consider **click-to-load** (do not auto-fetch remote
  images) to reduce exposure. Jurisdiction (where you and your users are) matters.

Net: users publish; you are the display intermediary. That usually means limited liability **if** you
have a takedown/hide path (the blocklist) and act on reports - but get it firmed up before going
image-heavy.

## Admin moderation queue: review images before they display (recommended for images)
For images, prefer **default-deny** over the text-style blocklist: hide every image until the
owner/admin approves it. This inverts the default and is the safer posture for untrusted remote
content.

- **Two complementary moderation models:**
  - *Blocklist (opt-out)* - show everything, hide specific bad items. Good for text (what we built).
  - *Approval queue (opt-in)* - hide all images until reviewed and approved. Good for images.
- **State lives in the backend, not on-chain.** A small store (a file or SQLite) maps a post id
  (the tx hash) -> `pending` / `approved` / `rejected`. The chain is immutable, so approve/reject
  only changes what YOUR feed renders; the post + image link stay permanent on-chain regardless. Each
  deployment moderates independently (a display-side editorial choice).
- **Feed behaviour.** An image renders only if its tx is `approved`; otherwise show an "image pending
  review" placeholder. The post's text can still display (or be gated too, your call).
- **Admin surface.** An authenticated view/endpoint listing pending image posts with approve/reject
  buttons; approving makes the image visible to everyone.
- **Admin authentication - two clean options (use either or both):**
  1. an **admin token/password** from an env var, checked on the admin endpoints; or
  2. a **network split**: expose the PUBLIC wall via Funnel/Cloudflare, but bind the ADMIN routes so
     they are reachable ONLY over your private Tailscale tailnet - admin is then protected by the
     network itself, no password needed.
- **Cost/effort.** More than the blocklist: persistent moderation state + admin auth + an admin UI +
  feed-gating by status. Well-scoped and worth it for images.

## Automated content detection (assist the moderation queue)
Automated classifiers can auto-triage image posts (auto-hide/flag the obvious ones, send the rest to
the human approval queue). Two very different categories:

- **Adult / NSFW (nudity, porn) - free, self-hostable:**
  - **NSFWJS** (Infinite Red) - free TF.js model, runs in Node or the browser; scores Porn / Hentai /
    Sexy / Neutral / Drawing. Best fit for this stack.
  - **Yahoo open_nsfw / open_nsfw2** - classic NSFW-score model (+ Keras/TF ports).
  - **NudeNet** - Python nudity detector/classifier (verify its current license/model availability).
  - **Hugging Face** models (e.g. Falconsai `nsfw_image_detection`) runnable locally with transformers.
  Use a score threshold; the backend fetches the posted image, classifies it, and auto-hides/flags
  high scores, leaving the rest for admin review.

- **CSAM (illegal child content) - SEPARATE and legally serious:** do NOT use a generic NSFW model.
  It is hash-matching against known material, with mandatory reporting duties in many jurisdictions
  (e.g. NCMEC in the US) once you are aware of it. Tools: **Cloudflare CSAM Scanning Tool** (free for
  sites on Cloudflare - relevant given the hosting choice), **Microsoft PhotoDNA** (free to eligible
  platforms, access-gated), NCMEC hash lists / Thorn Safer (larger platforms; Safer is paid). Get
  informed on your legal obligations before hosting public user images. (Not legal advice.)

Caveats: classifiers are imperfect (false positives/negatives) - use them to assist, never as the
sole gate (hence the human queue); detection means your server fetches + decodes untrusted images, so
sandbox it, cap size, and time out.

## Implementation sketch (when the time comes)
- Add an optional `img` field to the post metadata (`{a, m, ts, img?}`), forward-compatible with
  today's `{a, m, ts}` - old posts simply lack `img`, exactly like the optional `description` added
  to the proof-of-existence project.
- Validate `img` is an `https://` or `ipfs://` URL and within the 64-byte limit (or chunk it).
- UI: render the image with an `onerror` placeholder; gate behind click-to-load; run it through the
  same blocklist as text.
- Its own chapter: teach off-chain storage (IPFS/pinning), content addressing vs mutable URLs,
  integrity via hashes, and safe rendering of untrusted remote content.
