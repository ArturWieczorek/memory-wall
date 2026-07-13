# Chapter 10 - Two small wins: search + precise moderation

> Goal: two cheap, high-value additions. (1) **Search** the feed - filter the loaded posts by author
> or message as you type. (2) **Precise moderation** - let a curator hide *one exact post* by its
> transaction hash, not just anything containing a word.
>
> Written for a beginner - each idea gets a plain-language analogy.

---

## Part 1 - Client-side search

### The idea
The feed already loads the recent posts into the browser. Search just **filters what is already
there** by a case-insensitive substring of the author or message.

*Analogy:* Ctrl-F on the page you are already looking at - instant, no server round-trip.

### The honest boundary (important)
This searches **only the posts already loaded** (the recent window, ~20), **not the full on-chain
history**. Searching everything ever posted needs an **indexer/cache** (the provider's by-label
endpoint has no text search) - that is a separate, larger backlog item. We say so right under the
search box so nobody assumes it found everything. Being honest about a limit beats a search that
silently misses old posts.

### Built test-first
The logic is a pure function (`app/lib.ts`), trivially unit-tested (`app/lib.test.ts`):
```ts
export function filterPosts(posts: Post[], query: string): Post[] {
  const q = query.trim().toLowerCase();
  if (!q) return posts;                                  // empty query -> everything
  return posts.filter((p) => (p.author + " " + p.message).toLowerCase().includes(q));
}
```
The UI adds a search box above the feed, a "`N of M loaded posts match`" hint, and a "no matches"
message. The feed rendering is unchanged - we just pass it the filtered list.

## Part 2 - Precise moderation by transaction hash

### The idea
Chapter 06 gave us a **term** blocklist: hide any post whose author/message contains a word (a broad
brush - good for spam, but it also hides innocent posts that happen to mention the word). Sometimes
you want the opposite: hide **one specific post** and nothing else. Since Chapter 08 every post
carries its **transaction hash** (a unique id), so we can hide exactly that one.

*Analogy:* the term list is a net (catch everything mentioning X); the tx-hash list is tweezers
(remove this single item).

### Still display-side - it cannot erase anything
This hides the post from **our** feed only. The post is **permanent on-chain** and remains visible to
any other reader or frontend (recall the wall is permissionless - Chapters 07/08). Display-side
moderation is the only lever a chain-backed wall has, and it is both the power and the
responsibility. That is why this is a *curator* tool, configured by whoever runs the deployment.

### How it is wired
- Config: `wall.blocked-tx-hashes` (env `WALL_BLOCKED_TX_HASHES`), a comma-separated list.
- `Blocklist` now hides a post if its tx hash is on that list **or** its text matches a term. Matching
  is exact (case-insensitive, trimmed).
- It runs in the same place as before - when the backend serves `GET /api/feed`.

Test-first (`BlocklistTest`):
```java
Blocklist b = withTxHashes("ABC123");
assertThat(b.isBlocked(postWithTx("abc123"))).isTrue();   // exact match, case-insensitive
assertThat(b.isBlocked(postWithTx("def456"))).isFalse();  // a different post is untouched
```

## Running it
```bash
./gradlew spotlessApply test     # backend: 30 tests (incl. new tx-hash moderation)
cd ui && npm test                # UI: 26 tests (incl. filterPosts)
npm run typecheck && npm run build
```
To hide a specific post on a running backend:
```bash
WALL_BLOCKED_TX_HASHES=<the-bad-tx-hash> ./infra/run-backend.sh
```

## What to notice / common mistakes
- **Say what search does NOT cover.** A filter over the loaded window is not a full-history search;
  label it so, or users will trust a false negative.
- **Search is pure and client-side.** No new endpoint, no server load - it filters state already in
  the browser.
- **Moderation is display-side, always.** You can hide from your feed; you can never delete on-chain.
  Communicate that, and keep a takedown/report path.
- **Term list vs hash list.** Terms are broad (and can over-hide); a tx hash is exact. Use the right
  tool for the job.

## Glossary (Chapter 10)
- **Client-side filter** - narrowing a list already in the browser, with no server call.
- **Loaded window** - the recent posts currently fetched into the feed (not the full history).
- **Indexer** - a service that ingests and stores all on-chain records so you can search/paginate them
  quickly; needed for full-history search (backlog).
- **Curator moderation** - a deployment operator's choice to hide specific content from their feed.
- **Display-side moderation** - hiding content from what your site renders, without (being able to)
  remove it from the chain.
