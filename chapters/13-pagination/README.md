# Chapter 13 - Pagination ("Load more")

> Goal: the feed shows only the most recent ~20 posts. Add a **"Load more"** button that fetches the
> next page and appends it, so a growing wall is fully browsable.
>
> Written for a beginner - short; it builds on the existing feed.

---

## 1. The idea

The provider's "transactions by metadata label" endpoint is already **paged** (a count + a page
number). So pagination is mostly plumbing: pass a **page** through, and let the UI ask for the next
one and append the results.

*Analogy:* reading a long thread - you see the latest, and "Load more" pulls in the older batch below.

## 2. What we built

- **Backend:** `FeedReader.recent(int limit, int page)` (with a `recent(int limit)` default =
  page 1). `BlockfrostFeedReader` passes the page to the provider. `GET /api/feed?limit=20&page=2`
  forwards both (moderation + pin ordering still apply to the returned page).
- **UI:** a page counter; `loadFeed()` resets to page 1; **Load more** fetches the next page and
  **appends** it, de-duped by transaction hash. We infer "there may be more" from a **full page**
  (exactly `PAGE_SIZE` items) and hide the button when a short page comes back. The button shows only
  when the backend is online (the offline chain-read fallback stays single-page).

## 3. Honest boundaries
- **Pinning is a loaded-window concept.** Pins are ordered/capped within each page (Chapter 11), so a
  pinned post that lives on page 3 only rises to the top of page 3, not the whole wall. Pinning across
  *all* history - and true full-text search over everything - needs an **indexer** (still on the
  backlog). For a small wall (one page = everything), pins behave exactly as expected.
- **A page can yield fewer than `PAGE_SIZE` posts** even when more exist, because some transactions
  with our label are not valid posts (malformed, or hidden by moderation). The "full page => maybe
  more" heuristic can therefore stop one page early in rare cases; a real indexer removes the guessing.
- **Append can race** if new posts arrive between pages (a post could shift across the boundary), so
  we de-dupe by tx hash.

## 4. Tests
- API: `GET /api/feed` forwards the `page` param to the reader (`WallApiTest.feedForwardsPage` with a
  Mockito `eq(2)`); the existing feed/moderation tests now stub the two-arg `recent`.
- The actual paged provider call is integration (needs a live backend), like the rest of
  `BlockfrostFeedReader`.
- The UI append/de-dupe is thin glue over the tested feed; the paged fetch is exercised by hand
  (drive it in the running app).

## 5. Running it
```bash
./gradlew spotlessApply test     # backend: 41 tests
cd ui && npm test                # UI: 31 tests
npm run typecheck && npm run build
# with >20 posts on the wall, a "Load more" button appears under the feed.
```

## 6. What to notice / common mistakes
- **Reset vs append.** A refresh resets to page 1; "Load more" appends - keep the two paths separate.
- **Infer "more" carefully.** A full page is a reasonable "maybe more" signal, but it is a heuristic;
  say so, and reach for an indexer when it matters.
- **De-dupe on append.** Live data shifts under paging; key by tx hash.

## Glossary (Chapter 13)
- **Pagination** - fetching a long list in numbered pages instead of all at once.
- **Page / page size** - one batch and how many items it holds.
- **Loaded window** - the posts currently fetched into the browser (what search + pin ordering act on).
- **Indexer** - a service that ingests all on-chain records so you can search/paginate the full
  history quickly (backlog).
