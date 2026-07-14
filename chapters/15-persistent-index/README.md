# Chapter 15 - A durable index: survive restarts (optional SQLite store)

> Goal: Chapter 14 built an in-memory index of every post. It works, but it **forgets on restart** -
> every time the backend starts, it re-reads the whole wall from the chain. This chapter adds an
> **optional** persistent store (a single SQLite file) so a restart starts **warm**: it loads what it
> already knew, then only fetches what is new.
>
> Written for a beginner - each idea gets a plain-language analogy.

---

## 1. Why we need it (and why it is optional)

The in-memory index is a stack of cards we rebuild from scratch each morning. For a small wall that
is fine - re-reading a few hundred posts is a handful of provider calls. But as the wall grows, a
cold rebuild on every restart gets slower and hammers the provider each time.

*Analogy:* the in-memory index is notes on a whiteboard - instant to use, wiped when the lights go
out. A persistent store is the same notes in a **notebook**: when you come back you flip it open and
carry on, only writing down what happened while you were away.

We keep it **optional and off by default**, because "minimal dependencies" is a house rule and a
small wall genuinely does not need it. You turn it on by pointing the backend at a file.

## 2. The design - one small seam, two implementations

We introduced one interface so the index does not care *how* posts are stored:

```
interface PostStore {
  List<WallPost> loadAll();          // everything saved before (newest-first)
  void save(Collection<WallPost>);   // persist new posts (idempotent by tx hash)
}
```

- **`NoopPostStore`** (default) - stores nothing. `loadAll()` is empty, `save()` does nothing. With
  it the index behaves exactly as in Chapter 14: pure in-memory, re-ingests on restart, zero files.
- **`SqlitePostStore`** (opt-in) - one SQLite file, one `posts` table keyed by transaction hash.
  `save` is `INSERT OR IGNORE` (re-saving a known post is a harmless no-op); `loadAll` is
  `SELECT ... ORDER BY timestamp DESC`.

Which one you get is a single config value (`WallConfig.postStore`): blank `wall.index.db-path` ->
`NoopPostStore`; a file path -> `SqlitePostStore`. No conditional-bean magic, just a factory method.

## 3. How the index uses it

Two small changes to `WallIndex`:
- **Warm start (constructor):** seed the in-memory map from `store.loadAll()`. Best-effort - if the
  store cannot be read, we log nothing and start empty; the scheduled refresh refills from the chain.
- **Save on refresh:** after a refresh, `store.save(newlyAdded)` - only the posts we had not seen.
  With the no-op store this call does nothing, so the default path is unchanged.

Because the display layer (`Feed.forDisplay` / `Feed.newestFirst`) always re-orders by timestamp,
the map's insertion order is not load-bearing - so seeding from the store cannot corrupt ordering.

## 4. The trade-off we chose (and were honest about)

SQLite is the smallest possible "real database": a single file, no server, one dependency
(`org.xerial:sqlite-jdbc`). We deliberately did **not** reach for an ORM, a connection pool, or a
migration framework - the index's refresh is already single-threaded (`synchronized`), so a fresh
connection per call is simple and safe. The file is just a **cache of public on-chain data** - it
holds no secrets, and deleting it only forces one cold re-ingest.

## 5. The tests we wrote FIRST

- **`SqlitePostStoreTest`** (real temp-file DB, no chain): save-then-load roundtrip returns
  newest-first with every field intact; `INSERT OR IGNORE` makes a repeat save idempotent; a fresh
  store on the same file (a "restart") still sees the rows; posts without a tx hash are skipped; an
  empty save is a no-op.
- **`WallIndexTest`** (stubbed store + reader): the index **seeds** from `loadAll()` on construction;
  a refresh **saves only new** posts (skips ones already seeded); a store read failure at startup does
  **not** crash the app.

## 6. Running it
```bash
./gradlew spotlessApply test     # backend (adds the SQLite store + index seed/save tests)
cd ui && npm test                # UI unchanged: 33 tests

# In-memory (default) - re-ingests on restart:
./infra/run-backend.sh

# Durable - persist the index to a file (starts warm next time):
WALL_INDEX_DB_PATH=data/wall-index.db ./infra/run-backend.sh
```
The file and its parent directory are created on first run. It is a local cache - safe to delete
(you just pay one cold re-ingest) and safe to `.gitignore` (it holds only public chain data).

## 7. What to notice / common mistakes
- **Make it opt-in.** The dependency ships but stays inert until `wall.index.db-path` is set - the
  default backend is byte-for-byte the Chapter 14 behaviour.
- **Idempotency is the whole game.** Key on the tx hash and `INSERT OR IGNORE`, so re-saving the same
  post (which happens every refresh, since page 1 repeats) is free and safe.
- **Best-effort persistence.** A store error must never crash posting or reading - worst case the
  index falls back to re-reading the chain. Both the seed and the save are wrapped accordingly.
- **Do not store secrets.** The DB is public on-chain data only. Never widen it into a place that
  would hold keys or private config.
- **Order globally at display time**, not from the store - the store is just a cache; ordering is a
  view concern (`Feed`).

## Glossary (Chapter 15)
- **Persistent store** - data kept on disk so it survives a restart (vs in-memory, which is lost).
- **SQLite** - a full SQL database that lives in a single file, with no separate server process.
- **JDBC** - Java's standard API for talking to SQL databases.
- **Warm start / cold start** - starting with prior state already loaded vs starting empty and
  rebuilding from scratch.
- **Idempotent** - an operation you can repeat without changing the result (here: saving a post you
  already have).
- **`INSERT OR IGNORE`** - SQLite insert that silently skips a row whose primary key already exists.
