# Chapter 00 - Orientation

> Goal: understand what we are building and how, before any code. Reading only.

## 1. What is the Memory Wall?

A public, permanent message board on Cardano. Anyone posts a short message; it is recorded on-chain
and can be listed back as a feed, newest first. Think of a digital wall where messages are written in
indelible ink - once posted, a message is there forever.

The value here is the **message itself**, not money. That is what makes it a great testnet project:
a wall on a testnet is just as real an artifact as one on mainnet (only the coins are play money).

## 2. Two ways to build it
- **Metadata (we start here):** attach the message to a normal transaction as metadata. No smart
  contract needed - the simplest possible on-chain write. A working wall in one chapter.
- **dApp / datum (optional, later):** store each post as a UTxO at a script address, which unlocks
  on-chain rules like a posting fee, an NFT "receipt" per post, and a "pin" tier.

## 3. The architecture (web UI + wallet)

The front end is a **web page** (Next.js) that connects your **browser wallet** (Lace/Eternl) via
the CIP-30 standard - just like the mainnet wall you mentioned. But we keep the transaction-building
in **Java**: a small Spring backend builds the *unsigned* posting transaction and serves the feed; the
**wallet signs and submits** it. That split matters - the server never touches your keys; you keep
custody, and only sign what you choose to.

*Analogy:* the backend is a clerk who fills out the form (the transaction); you (the wallet) are the
only one who can sign it; the notice board (the chain) keeps the result, and the clerk reads it back
to show everyone.

## 4. What you will build
- Ch 01: post a message - chunk it to fit Cardano's metadata limit, and build the metadata (Java).
- Ch 02: read the feed - parse posts back and list them newest-first (Java).
- Ch 03: the backend API - Spring Boot endpoints to build an unsigned post tx and to serve the feed.
- Ch 04: the web UI - Next.js + CIP-30 wallet: connect, post (wallet signs), render the live feed.
- Ch 05: testnet + wrap-up - preprod config, mainnet notes, and optional extensions (dApp/datum
  storage, an NFT receipt, a fee + pin tier, curator moderation).

## 5. How this course works (same as the rest of the portfolio)
One chapter = one git commit + tag. Each chapter README is concept (+ analogy) -> what we build ->
tests we write FIRST -> steps -> what to notice -> glossary. TDD; keep it green; update PROGRESS.md;
plain ASCII; no Co-Authored-By trailer. (Counts like "N tests" are true at that chapter's tag.)

## 6. What is next
Chapter 01: posting a message - including the one real constraint, the 64-byte metadata limit, and
how we chunk around it.

## Glossary (Chapter 00)
- **Memory wall** - a public, append-only on-chain message board.
- **Transaction metadata** - structured data attached to a transaction (our simplest storage).
- **Feed** - the list of posts, read back from the chain newest-first.
- **dApp / datum version** - storing posts at a script address to enable on-chain rules (later).
