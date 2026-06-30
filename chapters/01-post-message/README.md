# Chapter 01 - Post a Message (metadata + chunking)

> Goal: turn a message into the on-chain metadata for a post, test-first, in pure Java. No wallet or
> network yet - this is the data layer the backend and UI build on.

## 1. A post, and the one real constraint

A post is just `{author, message, timestamp}`. We store it as transaction **metadata** under a label
(`1719`) - the simplest on-chain storage, no contract needed.

The one constraint that shapes the design: **a single Cardano metadata text value is capped at 64
bytes.** A message can be longer, so we split it into a **list of <=64-byte chunks**. Reassembling
the chunks in order gives back the original message. So a post's metadata is:
```
1719 -> { a: author, m: [ "chunk1", "chunk2", ... ], ts: timestamp }
```

## 2. What we build
- `WallPost` - the post record, with validation (author <=64 bytes, non-empty message).
- `Wall.chunk(message)` - split into <=64-byte UTF-8 chunks, never cutting a character in half.
- `Wall.postMetadata(post)` - the full metadata under label 1719.

## 3. Tests we write first (TDD)
- A short message is one chunk; a 200-char message splits into four <=64-byte chunks that reassemble
  to the original.
- **Multi-byte safety:** a message with `EUR` signs (3 bytes each) still chunks to <=64 bytes per
  piece and reassembles exactly - we never split a character.
- The serialized metadata embeds the author and the message text.
- `WallPost` rejects an empty message and an over-64-byte author.

## 4. Steps
- Write the tests, then `WallPost` (validating record) and `Wall` (chunking + metadata).
- Chunk by **code point**, accumulating UTF-8 bytes until the next character would exceed 64 - that
  is what keeps multi-byte characters intact.

## 5. What to notice / common mistakes
- **Chunk by bytes, not chars.** A naive `substring(0, 64)` can split a multi-byte character and
  corrupt the text. Always measure UTF-8 bytes and stop before overflowing.
- **Order matters.** The feed reader (Chapter 02) must join chunks in the same order to rebuild the
  message.
- This chapter only *builds* the metadata. Putting it on-chain happens later: the backend (Ch 03)
  assembles an unsigned transaction with this metadata, and the wallet (Ch 04) signs and submits it.

## 6. Build and commit
```bash
./gradlew spotlessApply test
git add -A && git commit -m "feat(ch01): wall post model + 64-byte chunking + metadata"
git tag ch01
```

## 7. What is next
Chapter 02 reads the feed: parse posts back out of metadata and list them newest-first.

## Glossary (Chapter 01)
- **Post** - `{author, message, timestamp}` recorded on the wall.
- **Metadata label** - the number namespacing wall posts (1719).
- **Chunk** - a <=64-byte slice of the message (the metadata string limit).
- **Code point** - a single character; we chunk by these so multi-byte characters stay whole.
