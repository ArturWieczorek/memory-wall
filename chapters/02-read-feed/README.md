# Chapter 02 - Read the Feed

> Goal: turn on-chain post metadata back into messages, and order them newest-first. The pure logic
> here (parse + sort) is what the backend's feed endpoint will serve.

## 1. The other half of the wall

Chapter 01 wrote a post; now we read it back. Reading a post means the inverse of posting:
- take the metadata map `{a, m: [chunks], ts}`,
- **rejoin the message chunks** in order (undoing the 64-byte split), and
- present posts **newest-first**.

## 2. What we build
- `Wall.parsePost(map)` - rebuild a `WallPost` from a metadata map (the inverse of `postMap`),
  concatenating the message chunks.
- `Feed.newestFirst(posts)` - order posts by timestamp, newest first.

## 3. Tests we write first (TDD)
- **Round-trip:** `parsePost(postMap(post))` equals the original post - including a 150-char message
  that was split into multiple chunks, proving the chunks rejoin exactly.
- **Ordering:** a mixed list comes back newest-first.

## 4. Steps
- Add `parsePost` to `Wall` (read `a`, join the `m` list, read `ts`).
- Add `Feed.newestFirst` (sort by timestamp descending; ISO-8601 strings sort chronologically, so a
  reversed string compare is correct).

## 5. What to notice / common mistakes
- **Rejoin in order.** The feed must concatenate chunks in the same order they were written, or a
  long message comes back scrambled. The round-trip test guards this.
- **ISO timestamps sort correctly as strings** (because they are fixed-width, zero-padded, UTC) - no
  need to parse them into dates just to sort.
- Actually *fetching* posts from the chain (querying metadata label 1719) needs a backend; that lives
  in Chapter 03's feed reader. Here we keep the pure parse + sort, which is fully unit-tested.

## 6. Build and commit
```bash
./gradlew spotlessApply test
git add -A && git commit -m "feat(ch02): parse posts back + newest-first feed"
git tag ch02
```

## 7. What is next
Chapter 03 builds the Spring Boot backend: an endpoint that builds an unsigned post transaction (for
the wallet to sign) and an endpoint that serves the feed.

## Glossary (Chapter 02)
- **parsePost** - rebuild a `WallPost` from its metadata, rejoining message chunks.
- **Feed** - posts ordered newest-first for display.
- **Round-trip** - post -> metadata -> parse back equals the original.
