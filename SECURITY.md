# Security Policy

Memory Wall is a testnet-first teaching project, but it is designed to be hosted publicly, so security
reports are welcome.

## Reporting a vulnerability
Please report privately - **do not open a public issue** for a vulnerability.
- Preferred: GitHub's **private vulnerability reporting** (the repo's Security tab -> "Report a
  vulnerability").
- Include: what you found, how to reproduce it, and the impact you expect.

You can expect an acknowledgement and, where valid, a fix or a documented mitigation.

## Design invariants (what should always hold)
- **The backend holds no keys and needs no funds.** Posters sign and pay with their own wallets; the
  server only builds an unsigned transaction and submits the wallet-signed one.
- **Secrets are env-only.** The Blockfrost project id (`WALL_BACKEND_PROJECT_ID`) and any real keys
  are never committed; `.gitignore` covers `.env`, `*.skey`, `*.seed`, `secrets/`, `*.db`, `*.sqlite`.
- **The backend binds to localhost** and is exposed only through a tunnel; input sizes are capped;
  the per-IP rate limiter does not trust a spoofable `X-Forwarded-For` hop.

## Known, accepted trade-offs
- **Moderation is display-side only.** Anything posted on-chain is permanent; the blocklist and
  tx-hash lever hide posts from *this* feed but cannot erase them from the chain.
- **UI dependency advisories that require a Next.js major bump** are deferred: the UI is a static
  export (no Next server runtime), so the outstanding server-side Next.js CVEs do not apply to the
  deployed site. Revisit if it is ever hosted on a Next server. See `AGENT.md`.
- **Images are not accepted yet** by design; the threat analysis (SSRF, CSAM/legal) is in
  `docs/IMAGE-POSTS-THREAT-ANALYSIS.md`.
