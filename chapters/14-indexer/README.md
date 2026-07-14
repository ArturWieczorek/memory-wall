# Chapter 14 - An indexer: full-history search + global pinning

> Goal: until now the feed showed only the most recent ~20 posts, so search and pinning acted only on
> that "loaded window". This chapter adds an **in-memory index** of *every* post, so the wall can
> **search the whole history** and **pin globally** (a pinned post rises above the entire wall, not
> just its page).
>
> Written for a beginner - each idea gets a plain-language analogy.

---

## 1. Why we need it

The provider's "transactions by metadata label" endpoint gives us pages of recent posts, but it has
**no text search** and returns posts a page at a time. So:
- **Search** could only filter the ~20 posts already in the browser.
- **Pinning** could only rank within a single page.

To do better we need *all* the posts in one place we control. That place is an **index**.

*Analogy:* the blockchain is a giant archive of loose papers in date order. An index is the **card
catalogue** we build over it - once we have it, we can find any card instantly and arrange them
however we like (pinned first, or by search term), instead of flipping through only the top of the
pile.

## 2. What we built - `WallIndex`

A small in-memory cache of every wall post:
- On a schedule it **pages the provider newest-first** and keeps each post it has not seen before
  (keyed by transaction hash, so no duplicates).
- The refresh is **incremental**: it stops as soon as a page contains nothing new (steady state) or
  the last page is reached (first run). So the very first refresh reads the whole history once, and
  every refresh after that is cheap - usually just the first page.
- It enriches each post exactly as the feed did (verified address + tip), reusing the reader.

`GET /api/feed` and the new `GET /api/search` are now served **from the index**:
- **Feed:** moderate -> order **globally** with `Feed.forDisplay` (pins across the whole wall) ->
  return the requested page.
- **Search:** moderate -> `Feed.search` (author/message contains the query, case-insensitive, over
  *all* posts) -> newest-first -> page.

*The pure building blocks (`Feed.forDisplay`, `Feed.search`, `Feed.page`) are unit-tested; the
`WallIndex` accumulate/stop logic is tested with a stubbed reader; the actual provider paging is
integration.*

## 3. The trade-off we chose (in-memory, not a database)

The index lives **in memory**, so it **re-ingests on restart**. That is fine at this scale (a full
re-read is a handful of provider calls) and keeps the backend dependency-free. A persistent store
(e.g. SQLite) would survive restarts and scale to huge walls, but adds a dependency and moving parts
we do not need yet - so it stays on the backlog. Being explicit about this trade-off matters: we
chose *simple and stateless-ish* over *durable*, on purpose.

## 4. The UI

- **Global pinning is automatic** - the UI already renders whatever `/api/feed` returns, and the feed
  is now globally ordered, so nothing changed in the UI for pinning.
- **Search now hits the backend.** When the backend is up and you type a query, the UI calls
  `/api/search` (debounced) and shows **full-history** results, with "Load more" paging them. When the
  backend is **offline**, it falls back to the old client-side filter over the loaded window (and says
  so). The search box's hint tells you which mode you are in.

## 5. Running it
```bash
./gradlew spotlessApply test     # backend: 51 tests (index + search/page/order helpers)
cd ui && npm test                # UI: 33 tests
npm run typecheck && npm run build
# with the backend running, GET /api/search?q=... searches every post; the feed pins globally.
```
Config (all optional, sensible defaults): `wall.index.refresh-ms` (60000), `wall.index.page-size`
(100), `wall.index.max-pages` (500).

## 6. What to notice / common mistakes
- **Refresh incrementally.** Re-reading the whole chain every tick would be wasteful; stop as soon as
  a page adds nothing new.
- **De-dupe by a stable key** (the tx hash) - the same post appears on page 1 every time.
- **Keep the ingest best-effort.** A provider hiccup during refresh must not crash the app; keep the
  last good cache and try again next tick.
- **Order globally, then paginate** - not the other way around, or pins/ordering would only be correct
  within a page.
- **Name the trade-off.** In-memory means "rebuilds on restart"; say so rather than pretending it is
  durable.

## Glossary (Chapter 14)
- **Indexer / index** - a service that reads all on-chain records into a store you control, so you can
  search and order them quickly.
- **In-memory cache** - data held in RAM (fast, but lost on restart).
- **Incremental refresh** - only fetching what is new since last time.
- **Global vs loaded-window** - operating over *all* posts vs only the ones currently fetched.
- **Full-history search** - searching every post ever, not just the recent page.
