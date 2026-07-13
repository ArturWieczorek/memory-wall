# Hosting the Memory Wall - run the backend and put it online

A step-by-step guide to make the deployed wall fully functional: run the Java backend on your own
box and expose it to the internet through a tunnel. The UI is already hosted (GitHub Pages); this is
the backend half.

> Agents: the current readiness state, decisions, and key files live in `AGENT-HANDOFF.md`. This file
> is the human runbook. The teaching version (networking concepts, analogies) is
> `../chapters/06-serve-it-from-home/README.md`.

## The shape

```
visitor's browser (Pages UI)  --HTTPS-->  a tunnel  -->  your home backend (127.0.0.1:8090)  -->  Blockfrost preprod
   window.__WALL_API__ -----------------> public URL       bound to localhost only                reads feed / builds tx
```

- The tunnel is the **only** way in - the backend refuses direct LAN connections (bound to localhost).
- The backend holds **no keys and no funds**. It needs a Blockfrost key only to *read* the chain and
  *build* transactions. Each poster **signs and pays** with their own wallet.
- The wall runs on **one network per deployment** (default: **preprod** - free test ADA). Mainnet is a
  pure config change (swap the provider URL + key), not a code change.

## Prerequisites
- **Java 21** (`java -version`).
- A **free Blockfrost preprod project id** (below).
- A **CIP-30 wallet** (Lace or Eternl) set to **Preprod**, with a little test ADA from the faucet.
- The **box stays on** while the wall is live.

## Step 1 - smoke-test the backend (no key, no config)
The backend boots and answers `/api/health` with zero configuration - a quick confidence check.

```bash
# Terminal 1 (this stays running - it IS the server; Ctrl+C to stop):
./gradlew run
# wait for: "Started WallApplication in 1.x seconds"

# Terminal 2 (a second, separate terminal):
curl 127.0.0.1:8090/api/health        # -> {"status":"ok"}
```
`/api/feed` will NOT work yet (no real provider configured) - that is expected. Stop it (Ctrl+C) and
continue.

## Step 2 - get a free preprod Blockfrost key
1. Sign up at https://blockfrost.io (free).
2. **Add project** -> Network = **Cardano Preprod** -> create.
3. Copy the **project id** (starts with `preprod`, ~30 chars).

This is a **secret**: never commit it, never paste it into a doc or chat. It only ever lives in an
environment variable on your box.

## Step 3 - run the backend for real
Use the helper - it requires only the key (as an env var) and sets sensible defaults for the rest:

```bash
export WALL_BACKEND_PROJECT_ID=preprod...     # your key from Step 2 (secret)
./infra/run-backend.sh
```

Verify from a second terminal (an empty wall returns `[]`):
```bash
curl http://127.0.0.1:8090/api/feed           # -> [] (or real posts)
```

Defaults the helper applies (override by exporting the variable before running):
| Variable | Default | Meaning |
|----------|---------|---------|
| `WALL_BACKEND_URL` | `https://cardano-preprod.blockfrost.io/api/v0/` | provider + network |
| `WALL_CORS_ORIGINS` | `https://arturwieczorek.github.io` | browser origin of the hosted UI |
| `WALL_BIND` : `WALL_PORT` | `127.0.0.1` : `8090` | localhost-only bind |

**Critical:** `WALL_CORS_ORIGINS` is an **origin** - scheme + host only, **no path** (use
`https://arturwieczorek.github.io`, not `.../memory-wall`). Adding the path is the #1 cause of
"blocked by CORS" in the browser.

## Step 4 - expose it with a tunnel
No port-forwarding, no public IP. Pick one:

**Option A - quickest test (Cloudflare quick tunnel; no account, no login):**
```bash
# install cloudflared once, then:
cloudflared tunnel --url http://localhost:8090
```
Prints a throwaway `https://<random>.trycloudflare.com`. Great for a first end-to-end test; the URL
changes every run.

**Option B - stable URL (Tailscale Funnel; the project's chosen path):**
```bash
curl -fsSL https://tailscale.com/install.sh | sh    # install (one-time)
sudo tailscale up                                    # opens a browser to log in
tailscale funnel 8090                                # serve port 8090 publicly over HTTPS
```
Gives a stable `https://<yourbox>.<tailnet>.ts.net`. If Funnel complains, Tailscale prints a link to
enable **HTTPS certificates + Funnel** in your admin console (a one-time toggle).

Verify the tunnel from anywhere:
```bash
curl https://<your-tunnel-url>/api/health           # -> {"status":"ok"}
```

## Step 5 - point the hosted UI at the tunnel
The Pages site is static, so you change its backend URL by editing `ui/public/config.js` and pushing
(the deploy workflow rebuilds in ~30s - you cannot edit files on Pages directly):

```js
// ui/public/config.js
window.__WALL_API__     = "https://<your-tunnel-url>";
window.__WALL_NETWORK__ = "preprod";
```
```bash
git add ui/public/config.js && git commit -m "config: point UI at the tunnel" && git push
```

## Step 6 - post from the wall
1. In Lace/Eternl, switch the wallet to **Preprod**.
2. Fund it from the **Cardano preprod faucet** (a little test ADA for the fee):
   https://docs.cardano.org/cardano-testnets/tools/faucet
3. Open https://arturwieczorek.github.io/memory-wall/ -> the status light should be **green** ->
   connect wallet -> write a message -> **Post to the wall** -> it appears in the feed.

## Keep in mind
- **Backend must stay running.** If the box or backend goes down, the site shows "offline"; visitors
  can still *read* by pasting their own Blockfrost key (the read-only chain fallback).
- **Rate limit behind Funnel** is coarse (Funnel may present one IP for all visitors) - fine for a
  hobby wall. Behind Cloudflare, set `WALL_CLIENT_IP_HEADER=CF-Connecting-IP` for per-visitor limits.
- **Moderation is display-side** (`WALL_BLOCKLIST`, comma-separated terms): it hides posts from *your*
  feed only - on-chain posts are permanent and cannot be deleted.
- **Keep the Blockfrost key secret** - environment variable only, never committed.
- **Going to mainnet:** set `WALL_BACKEND_URL` to a mainnet provider + a mainnet key and
  `__WALL_NETWORK__ = "mainnet"`. Same code; posters then spend real ADA on fees.
