# Hardening the wall behind Cloudflare - a beginner's guide

Your backend runs on your **home computer** and is reached through a **Cloudflare tunnel** at
`wall.arturwieczorek.com`. That means every request from the internet passes through **Cloudflare's
network first**, then down the tunnel to your box. This is great news for security: you can switch on
several free protections **at Cloudflare's edge**, so bad traffic is stopped *before* it ever reaches
your machine.

This guide explains, in plain language, what to turn on, what each thing protects you from, and
exactly where to click. No prior Cloudflare knowledge assumed.

## The big picture (why bother)

```
   the whole internet                Cloudflare (the guard at the door)         YOUR HOME BOX
   (visitors + bots + floods)  --->  DDoS shield, bot filter, rate limits  --->  the wall backend
```

*Analogy:* your backend is a small shop. Without Cloudflare, anyone can walk straight up to the
counter and hammer on it. With Cloudflare, there is a **security guard at the street entrance** who
turns away obvious troublemakers, limits how fast any one person can come in, and absorbs a stampede -
so your little shop inside stays calm.

**What we are actually protecting.** Your backend holds **no keys and no money** - so the danger is
not theft. It is:
- **Availability** - someone flooding it could knock your home box (and your home internet) offline.
- **Quota** - every read/build hits your free Blockfrost account; abuse could burn through it.
- **Noise** - bots and scanners constantly probe any public address.

Cloudflare's job here is to keep those three under control, cheaply and automatically.

**Two layers (defence in depth).** The backend *already* rate-limits per visitor (returns HTTP 429
after ~20 requests/minute, using the real visitor IP via `WALL_CLIENT_IP_HEADER=CF-Connecting-IP`).
The Cloudflare settings below are an **outer wall** that stops abuse earlier - before it costs you
anything. Having both is normal and good.

## Before you start: finding your way around the dashboard

1. Go to **https://dash.cloudflare.com** and log in.
2. On the account home, **click your domain tile** (`arturwieczorek.com`). Everything below is
   *per-domain*, so you must be "inside" the domain, not on the account overview.
3. **Easiest way to reach any setting - the search box.** At the **top of the page** there is a
   **search box** (with a magnifying glass, "Search..."). Type the feature name (e.g. `Bot Fight
   Mode`, `Rate limiting rules`, `Security Level`, `Cache Rules`, `Custom rules`) and press Enter - it
   takes you **straight to that page**, regardless of how the left menu is arranged. Each setting below
   lists the exact term to search.
4. **Or use the left-hand menu** if you prefer: the sections we use are **Security** (with sub-items
   WAF, Bots, Settings, DDoS), **Caching**, and **SSL/TLS**. Cloudflare occasionally moves these, which
   is why the search box (step 3) is the reliable route.

Now the settings, easiest first. For each: **what** it is, **what it protects from**, **where** (search
term + menu path), and **what to set**.

---

## 1. DDoS protection - already ON, nothing to do

- **What it is:** automatic absorption of a **flood** of traffic (a "Distributed Denial of Service"
  attack - thousands of machines hitting you at once to knock you offline).
- **Protects from:** your home box / home internet being overwhelmed by a flood.
- **Where:** Security -> DDoS. You will see it is **always on** for every Cloudflare site, free.
- **Set to:** leave the defaults. There is nothing to enable - just know it is working.

## 2. Bot Fight Mode - block junk bots

- **What it is:** a free filter that recognises and challenges **known bad bots** (scrapers,
  vulnerability scanners, spam bots) while letting real browsers and good bots (like search engines)
  through.
- **Protects from:** the constant background noise of bots probing your public address, and simple
  automated abuse.
- **Where:** **Security -> Bots** (older menu: Security -> Bots -> "Bot Fight Mode").
- **Set to:** toggle **Bot Fight Mode = On**.
- **Note:** this is the free tier. It is a blunt tool (no fine-tuning), which is fine for a hobby wall.

## 3. Rate limiting rule - the important one

- **What it is:** a rule that says "if a single visitor makes more than N requests in a short window,
  block them for a while." This is the **edge version** of what your backend already does - but it
  stops the flood at Cloudflare, so it never reaches your box or Blockfrost.
- **Protects from:** one person (or script) hammering the API to overload your machine or burn your
  Blockfrost quota.
- **Where:** **Security -> WAF -> Rate limiting rules -> Create rule** (search "Rate limiting" if you
  cannot find it). WAF = "Web Application Firewall".
- **Set to (a good starting point):**
  - **Name:** `wall-api-limit`
  - **If incoming requests match:** set the expression to
    `Hostname equals wall.arturwieczorek.com` **AND** `URI Path starts with /api/`
    (there is a simple "field / operator / value" builder - pick `Hostname`, `equals`,
    `wall.arturwieczorek.com`; then `+ And`, pick `URI Path`, `starts with`, `/api/`).
  - **When rate exceeds:** `60` requests per `1 minute`, counted **per client IP** (the "characteristics"
    / "counting" option - choose IP if offered).
  - **Then:** **Block** for `10 minutes` (or "Managed Challenge" if you prefer to challenge rather than
    hard-block).
  - **Deploy.**
- **Note:** the free plan allows **one** rate-limiting rule with basic options - which is exactly
  enough here. 60/min is generous (normal use is a few requests); tighten later if you see abuse.

## 4. Security Level - challenge known-bad IPs

- **What it is:** a dial for how suspicious Cloudflare should be. It uses a reputation score for each
  visitor IP; higher levels challenge more of the sketchy ones with a quick "are you human" check.
- **Protects from:** traffic from IPs with a bad reputation (known abusers, compromised machines).
- **Where:** **Security -> Settings** (older menu: the domain's "Security" or "Firewall" settings) ->
  **Security Level**.
- **Set to:** **Medium** (the default). "High" is more aggressive if you get abused; "Essentially Off"
  is not recommended.

## 5. Cache: don't serve stale API responses

- **What it is:** a rule telling Cloudflare **not to cache** your API. By default Cloudflare does not
  cache dynamic responses, but an explicit "bypass" rule guarantees your feed and health-check are
  always fresh (never a stale copy from Cloudflare's cache).
- **Protects from:** a visitor seeing an **out-of-date feed** or a wrong "online/offline" status
  because Cloudflare served a cached response.
- **Where:** **Caching -> Cache Rules -> Create rule** (search "Cache Rules").
- **Set to:**
  - **When incoming requests match:** `Hostname equals wall.arturwieczorek.com` AND `URI Path starts
    with /api/`
  - **Then:** **Bypass cache** (or set "Cache eligibility = Bypass cache").
  - **Deploy.**
- **Note:** this is about freshness, not attack protection - but it prevents a confusing "why is my
  feed old?" surprise.

## 6. (Optional) WAF custom rule - only allow the methods you use

- **What it is:** a firewall rule that blocks HTTP methods your API never uses. The wall only uses
  `GET`, `POST`, and `OPTIONS` (the browser's CORS pre-check). Anything else (`PUT`, `DELETE`,
  `TRACE`, odd probes) can be blocked at the edge.
- **Protects from:** scanners and exploit attempts that poke at unusual methods.
- **Where:** **Security -> WAF -> Custom rules -> Create rule**.
- **Set to:**
  - **Expression:** `Hostname equals wall.arturwieczorek.com` AND `Request Method is not in {GET POST
    OPTIONS}`
  - **Then:** **Block.**
- **Note:** optional and belt-and-suspenders. Your backend already ignores methods it does not handle;
  this just refuses them earlier.

## 7. (Bonus) SSL/TLS mode

- **What it is:** how Cloudflare talks to your "origin". With a **cloudflared tunnel**, the connection
  from Cloudflare to your box is already encrypted by the tunnel itself, and visitors always get HTTPS
  to Cloudflare.
- **Where:** **SSL/TLS -> Overview**.
- **Set to:** **Full** (or Full (strict)) is fine. Do **not** set "Off" or "Flexible". With a tunnel
  this usually needs no change - just confirm it is not "Off".

---

## What NOT to turn on

- **Cloudflare Access / Zero Trust** (putting the hostname behind a login): this would force every
  visitor to authenticate before reaching the wall - which **breaks public posting**. The wall is
  meant to be open, so leave the API public and rely on the rate/bot/DDoS layers above.

## How to check it is working

- **See what Cloudflare is blocking:** Security -> **Events** (or "Security Events"). After some
  traffic you will see challenges/blocks logged here, with the rule that fired.
- **Test the rate limit yourself** (from a machine, not your own box, to use a normal IP):
  ```bash
  for i in $(seq 1 120); do curl -s -o /dev/null -w "%{http_code} " https://wall.arturwieczorek.com/api/health; done; echo
  ```
  You should see `200`s turn into `429` (your app) or `403`/challenge (Cloudflare's rule) once you pass
  the limit. Wait for the block window to expire before normal use.

## Quick checklist
- [ ] 1. DDoS - confirm it is on (automatic).
- [ ] 2. Bot Fight Mode - **On** (Security -> Bots).
- [ ] 3. Rate limiting rule on `wall.arturwieczorek.com` + `/api/` (Security -> WAF -> Rate limiting).
- [ ] 4. Security Level - **Medium** (Security -> Settings).
- [ ] 5. Cache Rule - **Bypass** for `/api/` (Caching -> Cache Rules).
- [ ] 6. (optional) WAF rule - block non GET/POST/OPTIONS (Security -> WAF -> Custom rules).
- [ ] 7. SSL/TLS - **Full** (SSL/TLS -> Overview).
- [ ] Do NOT enable Cloudflare Access on this hostname.

## Glossary
- **Edge:** Cloudflare's global servers that sit between visitors and your box.
- **DDoS:** a flood of traffic meant to knock a service offline.
- **WAF (Web Application Firewall):** rules that inspect and filter incoming web requests.
- **Rate limiting:** capping how many requests one visitor may make in a time window.
- **Managed Challenge:** a quick, usually invisible "are you human" check Cloudflare shows suspicious
  visitors (no CAPTCHA to solve in most cases).
- **Origin:** your actual server (here, the home box reached via the tunnel).
- **Bypass cache:** tell Cloudflare to always fetch fresh from the origin, never serve a stored copy.
