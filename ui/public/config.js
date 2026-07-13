// Runtime config for the Memory Wall UI - edit these AFTER deploying; no rebuild needed.
//
// __WALL_API__ : the Java backend's public base URL (your Tailscale Funnel / Cloudflare URL).
//   Leave "" to call /api on the same origin (local dev uses the Next.js proxy in next.config.mjs).
//   Example: window.__WALL_API__ = "https://mybox.tailXXXX.ts.net";
//
// __WALL_NETWORK__ : which network the read-only fallback queries (preprod | preview | mainnet).
// NOTE: this is an ephemeral Cloudflare "quick tunnel" URL - it changes each time cloudflared
// restarts. For a stable address, switch to Tailscale Funnel (see infra/HOSTING.md) and update this.
window.__WALL_API__ = "https://solid-beds-lamps-psp.trycloudflare.com";
window.__WALL_NETWORK__ = "preprod";
