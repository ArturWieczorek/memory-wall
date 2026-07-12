# Chapter 06 - Serving the wall from your own machine (networking, made friendly)

> Goal: take the wall from "runs on my laptop" to "anyone on the internet can visit and post to it" -
> using **your own computer as the server**, with **no rented VPS** and **no domain required**. Along
> the way you will learn the networking basics every web app quietly relies on: clients and servers,
> IP addresses and ports, `localhost`, why your home machine is hidden from the internet, tunnels,
> HTTPS, CORS, health checks, rate limiting, moderation, and graceful degradation.
>
> Written for a complete beginner - every term is introduced with a plain-language analogy the first
> time it appears. No prior networking knowledge assumed.

---

## 1. Two halves: the client and the server

The wall has two parts:
- the **UI** - the page that runs inside each visitor's web browser (buttons, the feed, the wallet
  pop-up);
- the **backend** - a Java program that runs somewhere and answers requests (builds the posting
  transaction, serves the feed).

*Analogy:* the UI is the **storefront** customers walk into; the backend is the **back office** that
actually does the work. A customer never sees the back office; they just get served through the
counter.

Why does this project need a back office at all, when Proof of Existence was a single static page?
Because the wall *does work on request* - it builds transactions and reads a live feed. A static file
host can only hand out pre-made files; it cannot run a program. Running a program that listens for
requests is exactly what "a server" means.

## 2. The vocabulary of networking

**IP address** - a computer's "phone number" on a network, like `203.0.113.7`. Every device that
talks on the internet has one.

**Port** - an "apartment number" at that address. One computer runs many programs at once; a port
says *which* program a message is for. Our backend listens on port **8090**.
*Analogy:* the building is the IP address; the apartment is the port. "Deliver to 203.0.113.7 : 8090"
means "the building at that address, apartment 8090."

**localhost / 127.0.0.1** - a special address meaning "this very machine." When you start the backend
and open `http://localhost:8090`, your browser talks to the program on *your own computer*. Nobody
else on earth can reach *your* `localhost` - by definition it never leaves the machine.

**Your home router and NAT** - at home, all your devices share **one** public IP address (the one
your internet provider gave the house). The router performs **NAT** (Network Address Translation): it
lets your devices reach *out* to the internet, but by default the internet cannot reach *in* to a
specific device.
*Analogy:* an apartment building with one street address and no buzzer list for outsiders. Residents
can walk out and mail letters, but a stranger on the street has no way to ring a specific apartment.

That last point is the whole problem this chapter solves: **"just run it at home" is not reachable
from the internet.** You *could* poke a hole in the router (port-forwarding), but it is fiddly and a
security risk (you would be exposing your home network directly). We will use a nicer trick instead.

## 3. Making your home box reachable: tunnels (no VPS, no port-forwarding)

The trick reverses the direction. Instead of the internet connecting **in** to you (blocked by NAT),
**your machine connects out** to a helper service, and that service relays visitors back down the
connection your machine already opened.

*Analogy:* you cannot receive mail at a hidden address, so you use a forwarding service: it has a
public address, and anything sent there is passed along to you through a channel you opened. Except
here it is free and automatic.

Two good, free options (we compared them in earlier discussion):

- **Tailscale Funnel** - your machine runs a small agent that dials out to Tailscale; Funnel then
  publishes a **public HTTPS URL** like `https://mybox.tailXXXX.ts.net` that relays to your local
  backend. Free, a **stable** name, **no domain needed**. A visitor does *not* need Tailscale - Funnel
  is the public mode.
- **Cloudflare Tunnel** (`cloudflared`) - the same idea; public under **your own domain**
  (`api.yoursite.com`). Without a domain you only get an *ephemeral* `trycloudflare.com` URL that
  changes on restart.

Recommendation for a free, no-domain setup: **Tailscale Funnel** (stable URL beats Cloudflare's
ephemeral one; its fair-use traffic limit is irrelevant for a small wall). A bonus: the tunnel hides
your home IP - visitors see the relay's address, not yours.

## 4. HTTPS / TLS - the padlock

**HTTPS** is HTTP wrapped in **TLS**: it encrypts the traffic (nobody in between can read it) and
proves the server's identity (nobody can impersonate it).
*Analogy:* a sealed, tamper-evident envelope instead of a postcard anyone can read.

Browsers require HTTPS for many features (and users expect the padlock). The good news: **the tunnels
give you HTTPS for free** - Tailscale Funnel and Cloudflare terminate TLS at their edge, so you get a
valid `https://` URL with zero certificate work on your side.

## 5. CORS - why the browser blocks cross-site calls (and how we allow them)

Now your UI lives on one origin (say `https://my-wall.pages.dev`) and your backend on another
(`https://mybox.ts.net`). An **origin** is the scheme + host + port together; these two are different
origins.

Browsers enforce the **same-origin policy**: a page may not read responses from a *different* origin
unless that origin explicitly opts in with **CORS** (Cross-Origin Resource Sharing) headers.
*Analogy:* your bank won't act on instructions phoned in by a stranger on your behalf unless you have
pre-authorised that caller.

**Preflight.** For "non-simple" requests (a custom header, a JSON body, methods like POST) the browser
first sends a tiny `OPTIONS` "may I?" request - the **preflight** - and the server answers with
`Access-Control-Allow-*` headers. Only then does the real request go. In DevTools you see this as a
`204 No Content` row (the preflight) followed by the real `200`. That is normal, not a bug - if you
ever see doubled calls with a 204, that is the preflight. (This is the exact mechanism that blocked
Koios in the Proof of Existence project: Koios never sent the allow header, so the browser refused.)

**What we added.** `WallConfig` opts the API in for the UI's origin:

```java
registry.addMapping("/api/**")
    .allowedOrigins(props.corsAllowedOrigins().toArray(new String[0])) // e.g. your UI origin, or "*"
    .allowedMethods("GET", "POST", "OPTIONS");
```

Configure it with `wall.cors-allowed-origins` (env `WALL_CORS_ORIGINS`). `"*"` allows any origin,
which is fine here because the API carries no cookies or credentials; tighten it to your exact UI URL
if you prefer. Without this, the hosted UI's `fetch` would fail with a bare "Failed to fetch."

## 6. Is the server even up? Health checks + a status light

Because the backend is *your home box*, it can be switched off. So the UI needs to know. We added a
trivial endpoint:

```java
@GetMapping("/health")
public Map<String, String> health() { return Map.of("status", "ok"); }
```

The UI polls `GET /api/health` on load and every 30 seconds and shows a coloured dot - **green** when
it responds, **red** when it does not - and when red it disables the Post button and shows a banner.
*Analogy:* the "Open / Closed" sign on a shop door.

This is **graceful degradation**: when a part is down, the app stays honest and as-useful-as-possible
instead of silently breaking.

## 7. Still readable when the box is off: the chain fallback

There is a catch: our backend also *serves the feed*, so if it is off, reading dies too. We fix that
by letting the UI read the feed **directly from the chain** when the backend is down.

The browser calls **Blockfrost** (which, unlike Koios, sends CORS headers, so a browser can call it),
using a free project id the visitor pastes, and asks for all transactions under our metadata label:

```
GET https://cardano-preprod.blockfrost.io/api/v0/metadata/txs/labels/1719?order=desc&count=20
    header: project_id: <the visitor's own key>
```

then rebuilds each post from its JSON metadata (the same `{a, m, ts}` shape the backend parses).
*Analogy:* if the guided-tour desk is closed, you can still walk the public galleries yourself.
This is **read-only** - posting still needs the backend (that is where the transaction is built) - and
the key is the visitor's own, used only from their browser, never stored.

## 8. Protecting a public box: rate limiting

A public endpoint invites bots and abuse. We cap how many API requests one client may make per minute
and reply **HTTP 429 (Too Many Requests)** beyond that. It is a simple per-IP fixed-window counter
(`RateLimiter`), applied by a servlet filter (`RateLimitFilter`); the health check is exempt.

One networking subtlety: **behind a tunnel/proxy, the socket IP is the tunnel's**, not the visitor's.
The real client IP arrives in the `X-Forwarded-For` header, so we read its first hop:

```java
String xff = req.getHeader("X-Forwarded-For");
return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
```

*Analogy:* caller ID passed through a switchboard - the switchboard's number shows on the socket, but
the original caller's number is written in the header. Configure with `wall.rate-limit.*`.

## 9. Moderation on a permanent ledger: the blocklist

Anyone can post, and posts are **permanent on-chain** - you cannot delete them. But you *do* control
what **your** feed displays. `Blocklist` hides any post whose author or message contains a configured
term (`wall.blocklist`).
*Analogy:* you cannot un-print a newspaper, but you choose what to pin on your noticeboard.

It is display-side only - the post still exists on the chain; your feed simply does not show it. It is
also the seam where a "report this post" / takedown flow would plug in. (For the stronger,
approval-before-display model - especially for future image posts - see `docs/future-images.md`.)

## 10. Do not bake the URL in: runtime config

The UI is built once and served as static files, but your backend's tunnel URL might change. So the
UI reads it **at runtime** from `public/config.js` rather than hard-coding it:

```js
window.__WALL_API__ = "https://mybox.tailXXXX.ts.net"; // edit after deploy - no rebuild
window.__WALL_NETWORK__ = "preprod";
```

*Analogy:* a whiteboard note by the door you can update in seconds, versus repainting the sign every
time. Empty (`""`) means "same origin," which is what local dev uses (the Next.js proxy).

## 11. Do it for real - step by step

```bash
# 1. Start the backend (a Cardano provider configured via WALL_BACKEND_URL - Yaci DevKit locally,
#    or a Blockfrost-compatible testnet URL).
./gradlew run
#    -> log: "Started WallApplication ... on port 8090"

# 2. Confirm it is alive (on the same machine):
curl http://localhost:8090/api/health
#    -> {"status":"ok"}

# 3. Expose it to the internet from your own box (no VPS):
tailscale funnel 8090
#    -> https://mybox.tailXXXX.ts.net  (public, HTTPS, stable)
#    Confirm from anywhere:  curl https://mybox.tailXXXX.ts.net/api/health  -> {"status":"ok"}

# 4. Tell the UI where the backend is (edit the static config, no rebuild):
#    ui/public/config.js:  window.__WALL_API__ = "https://mybox.tailXXXX.ts.net";

# 5. Let the UI's origin through CORS when you start the backend:
WALL_CORS_ORIGINS="https://my-wall.pages.dev" ./gradlew run

# 6. Deploy the UI (static): Cloudflare Pages or GitHub Pages; visit it.
#    The status light turns green; connect a wallet; post. Others can now post too.
```

(Recording a real post needs the wallet + a funded key; a full public-testnet run is the natural next
step. The pieces above are all unit-tested and the UI builds clean.)

## 12. Before you make it public - a safety checklist

- **Rate limit on** (default) and tuned for your expected traffic.
- **Moderation** blocklist ready, and a plan for handling reports (and read `docs/future-images.md`
  before ever adding images).
- **No secrets in the repo** - by design the backend holds no keys (the wallet signs), which is a big
  part of why this is safe to expose. Double-check nothing sensitive is committed and **run a secrets
  audit of the whole repo (and its git history) before flipping it public.**
- **CORS scoped** to your UI origin if you want to be strict (or `"*"` if you truly want any site to
  use your API).
- **Your box is now exposed** through the tunnel - the tunnel hides your home IP (traffic goes via the
  relay), and the backend only builds unsigned transactions + reads a public feed, so the blast radius
  of the public endpoint is small. Keep the machine patched.

## 13. What we tested
- `RateLimiterTest` - allows up to the limit, blocks beyond, resets each minute, per-IP.
- `BlocklistTest` - hides matching posts (case-insensitive), keeps order, empty list hides nothing.
- `WallApiTest` - `/api/health` is ok; a cross-origin request gets the CORS header.
- `WallModerationTest` - the feed actually hides blocklisted posts end-to-end.
- UI: `tsc --noEmit` clean and `next build` succeeds.

## Glossary (Chapter 06)
- **Client / server** - the browser UI (client) vs the always-listening backend program (server).
- **IP address / port** - a machine's network address, and which program on it a message is for.
- **localhost (127.0.0.1)** - "this same machine"; not reachable by anyone else.
- **NAT / home router** - shares one public IP and, by default, blocks incoming connections to a
  specific device.
- **Tunnel (Tailscale Funnel / Cloudflare)** - your machine dials out to a relay that publishes a
  public URL, so no port-forwarding or VPS is needed.
- **HTTPS / TLS** - encrypted, identity-verified web traffic; the tunnels provide it free.
- **Origin / same-origin policy** - scheme+host+port; the browser rule that isolates sites.
- **CORS / preflight** - how a server opts in to cross-origin calls; the `OPTIONS` "may I?" the
  browser sends first (the 204 you see before the 200).
- **Health check** - a cheap endpoint the UI polls to show online/offline (graceful degradation).
- **Rate limiting / 429** - capping requests per client to resist abuse; `X-Forwarded-For` carries the
  real IP behind a proxy/tunnel.
- **Blocklist** - display-side moderation; hide from your feed what you cannot delete from the chain.
- **Runtime config** - settings read when the page loads (not baked into the build), so a URL change
  needs no rebuild.
