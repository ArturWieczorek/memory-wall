# Chapter 08 - Who really posted this? (verified author identity)

> Goal: make the "who" on a post **trustworthy**. Today the author is free text - anyone can sign a
> post as "Satoshi". We keep that friendly name but add, beside it, the **address that actually
> signed the transaction**, read from the chain so it cannot be faked. The feed then shows the name
> as a *claim* and the address as *proof*.
>
> Written for a beginner - each term is introduced with a plain-language analogy the first time it
> appears.

---

## 1. The problem: a name is a claim, not proof

When you post, you type a name. That name is stored verbatim in the transaction metadata and shown in
the feed. Nothing checks it. Worse, because the wall is **permissionless** (Chapter 06/07: anyone can
write the metadata themselves with the command line, not just through our web page), the name is
trivially forgeable - a script can post as "Ada Lovelace" or "The Mayor" with no barrier at all.

*Analogy:* a name typed on a visitor sign-in sheet. It might be true; nothing stops someone writing
whatever they like.

So how do you show *who really posted*? With something the poster cannot fake: the **wallet address
that paid for and signed the transaction**. On Cardano, a transaction can only spend a UTxO if it is
signed by the key that controls that address - so the input address is cryptographic proof of who
authorised the post. That is the identity we surface.

*Analogy:* the name on the sign-in sheet is a claim; the security-badge swipe that unlocked the door
is proof. We show both, clearly labelled.

## 2. Why the address must come from the transaction (not from a field the poster fills in)

A tempting shortcut is to have the poster's address written into the metadata (we already know it
while building the post). **Do not do this for identity.** Metadata is just text the poster supplies -
a command-line user can put *any* address there, so a "metadata address" is exactly as spoofable as
the name. The only trustworthy source is the **transaction's input**, which the chain itself
guarantees was signed. So we read it back from the chain when building the feed.

## 3. What we build

Backend (`WallPost`, `BlockfrostFeedReader`):
- `WallPost` gains an optional 5th field `address`. (As in Chapter 07, we add convenience
  constructors so every existing caller keeps compiling - 3 args, 4 args, or the full 5.)
- When building the feed, for each post we look up its transaction's UTxOs and take the **first input
  address** - the payer. It flows into the feed JSON automatically.
- The lookup is **best-effort**: if the provider call fails, `address` stays empty and the feed still
  renders (a post simply shows no verified chip). One extra lookup per post - see the cost note below.

UI (`app/lib.ts`, `FeedList`):
- `shortenAddress("addr_test1qpw...")` -> `"addr_test1qp...lz6aa7"` (a full address is ~100 chars -
  far too long to show whole).
- `explorerAddrUrl(network, address)` -> the cardanoscan **address** page for the wall's network
  (we refactored the shared host out of `explorerTxUrl`).
- The feed renders: **name** + `(claimed)` + a **verified** chip showing the short address, linking to
  the explorer, with the full address on hover.

## 4. Tests first

The logic is pure and unit-tested (`app/lib.test.ts`):
```ts
expect(shortenAddress("addr_test1qpw0djgj0x59...lz6aa7")).toBe("addr_test1qp...lz6aa7");
expect(explorerAddrUrl("preprod", "addr_test1qxyz"))
  .toBe("https://preprod.cardanoscan.io/address/addr_test1qxyz");
```
The display is checked with React Testing Library (`FeedList.test.tsx`) - the name is "(claimed)",
the address is a "verified" link to the explorer with the full address in its `title`, and when there
is no address the chip is absent:
```tsx
const addrLink = screen.getByRole("link", { name: "addr_test1qp...lz6aa7" });
expect(addrLink).toHaveAttribute("href", `https://preprod.cardanoscan.io/address/${ADDR}`);
```
Backend: `WallPost` carries `address` (defaulting empty), and `GET /api/feed` includes it in the JSON
(`WallApiTest`). The actual chain read in `BlockfrostFeedReader` needs a live provider, so - like the
rest of that class - it is exercised against a real backend, not in the unit tests.

## 5. The privacy trade-off (why this is optional)

Surfacing the payer address is a real trade-off, not a pure win:
- It **links all of a person's posts together** and to a wallet whose balance and history anyone can
  look up on the explorer. Someone who wants their message *not* tied to their funds may dislike this.
- It does **not stop name spoofing** - it adds a verifiable fact next to the claim. The name remains
  free text.

That is why the wall ships this as an *enhancement*: it improves trust for those who want it, at a
privacy cost you should understand. A future refinement could show the **stake address** (which groups
a wallet) or let a poster opt out.

## 6. Cost note (and how it is fixed later)

Reading each post's input address is one extra provider call per post - so a 20-post feed makes ~20
extra lookups on load. Fine for a small wall; for a busy one this is exactly what the backlog's
**indexer/cache** item solves (fetch once, store the address, serve instantly). We keep the lookup
best-effort so it never blocks or breaks the feed.

## 7. Running it
```bash
./gradlew spotlessApply test     # backend: 28 tests
cd ui && npm test                # UI: 23 tests (lib + FeedList)
npm run typecheck && npm run build
```

## 8. What to notice / common mistakes
- **Claim vs proof.** Never present the typed name as identity. Show it as a claim; show the
  chain-verified address as proof.
- **Read identity from the transaction, not from metadata.** A metadata address is as fakeable as the
  name.
- **Best-effort enrichment.** An identity lookup must never break the core feed - swallow failures and
  fall back to no chip.
- **Shorten, but keep the full value reachable.** Show `addr...tail`, put the full address in the link
  and the `title` (hover), and link to the explorer.
- **Mind privacy.** Publishing the payer address is a deliberate trade-off; document it.

## Glossary (Chapter 08)
- **Address (payment address)** - where value lives on Cardano; here, the one that funded/signed the
  post. Long (~100 chars), starts with `addr` (or `addr_test` on testnets).
- **Input / UTxO** - the coin a transaction spends. Spending it requires a signature from the key that
  controls its address - which is why the input address proves who signed.
- **Payer address** - the address whose UTxO paid for the transaction (its first input here).
- **Stake address** - an address that groups all of one wallet's payment addresses; a possible future
  identity that is more stable than a single payment address.
- **Claim vs proof** - self-reported data (the name) versus data the system can verify (the signing
  address).
- **Best-effort** - an operation that may quietly fail without breaking the surrounding feature.
