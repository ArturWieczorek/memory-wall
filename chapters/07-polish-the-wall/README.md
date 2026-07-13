# Chapter 07 - Polishing the wall (and learning to test the UI)

> Goal: take the working-but-plain wall and make it feel finished - a **dark/light theme**, **"time
> ago"** timestamps, a **byte counter** so you know your message fits, a **"view tx" link** to a block
> explorer, a **network label**, and a **friendly empty state**. Just as important: we give the
> **front end its first tests** (Vitest + React Testing Library) and build every new piece
> test-first, the same red-green-refactor way we build the Java side.
>
> Written for a beginner - each tool and term is introduced with a plain-language analogy the first
> time it appears.

---

## 1. Why polish, and why tests first

The wall already works: connect a wallet, post, read the feed. But a public site is judged in
seconds - a plain page reads as "unfinished", and small rough edges (no idea how long your message
can be, cryptic timestamps, no way to verify a post on-chain) quietly erode trust.

We could just start editing the page. But the UI so far had **no tests** - only a type-check. A
type-check tells you the code *compiles*; it does not tell you the code *behaves* (that "3h ago" is
right, that a bad feed row is skipped, that the explorer link points at the correct network). So the
first thing we build is a place to test the front end.

*Analogy:* the backend already has a workbench (JUnit) where we clamp a part and check it before
bolting it on. This chapter builds the **same workbench for the browser code**.

## 2. A test workbench for the front end (Vitest + React Testing Library)

Two new dev-only tools:
- **Vitest** - a test runner for JavaScript/TypeScript (like JUnit for Java). It finds `*.test.ts`
  files, runs them, and reports pass/fail.
- **React Testing Library (RTL)** - renders a React component into a fake in-memory browser
  (**jsdom**) so a test can ask "is the text 'Ada' on the screen? does the link point here?" the way
  a user would look at it.

Install (dev dependencies - they never ship to visitors):
```bash
cd ui
npm install --save-dev vitest @vitejs/plugin-react @testing-library/react @testing-library/jest-dom jsdom
```
Config (`ui/vitest.config.ts`) tells Vitest to use the jsdom browser and load a setup file that adds
friendly matchers like `toBeInTheDocument()`. Two scripts in `package.json`:
```json
"test": "vitest run",         // run once (used by CI)
"test:watch": "vitest"        // re-run on save while developing
```

> Note: pulling in Vitest also pulled a newer Vite, which wanted a newer `@types/node`, so we bumped
> that dev dependency. That is normal - tools travel with their own dependency expectations.

## 3. Pure helpers, built test-first (`app/lib.ts`)

The trick to easy UI testing: pull the **logic** out of the component into small **pure functions**
(same input -> same output, no screen, no network). Those are effortless to test. We created
`ui/app/lib.ts` with:

- `byteLength(s)` - UTF-8 **bytes** of a string (Cardano and the backend measure the message in
  bytes, not characters - `"e"` is 1 byte but `"e-acute"` is 2).
- `relativeTime(iso, nowMs)` - "just now" / "5m ago" / "3h ago" / "2d ago" / a date for old posts. We
  pass `nowMs` in (instead of calling the clock inside) so the function is **deterministic** and a
  test can pin "now".
- `explorerTxUrl(network, txHash)` - the cardanoscan URL for the wall's network.
- `rowToPost(row)` - rebuild a post from a raw chain row (used by the offline read fallback).
- `resolveInitialTheme(stored, prefersDark)` / `nextTheme(t)` - the dark-mode decision logic.

**Test-first**, the rhythm looks like this (`app/lib.test.ts`):
```ts
it("reports minutes, hours, and days", () => {
  const now = Date.parse("2026-07-13T12:00:00Z");
  expect(relativeTime("2026-07-13T11:57:00Z", now)).toBe("3m ago");
  expect(relativeTime("2026-07-13T09:00:00Z", now)).toBe("3h ago");
});
```
Write the expectation, run `npm run test:watch` (red - function does not exist yet), implement the
smallest thing that passes (green), tidy up (refactor). Run `npm test` for the whole suite.

## 4. Showing the transaction ("view tx") - and why author is not trustworthy

We want each post to link to a block explorer so anyone can see it on-chain. For that the feed needs
the **transaction hash** of each post - and today's feed did not carry it.

Backend change (`WallPost`): add an optional `txHash` field. Records in Java have a fixed
constructor, so we add a **convenience 3-argument constructor** that defaults `txHash` to empty -
that way every existing caller (and test) keeps compiling, and only the feed reader fills it in:
```java
public record WallPost(String author, String message, String timestamp, String txHash) {
  public WallPost(String author, String message, String timestamp) {   // convenience: no hash yet
    this(author, message, timestamp, "");
  }
}
```
The feed reader (`BlockfrostFeedReader`) already has each transaction's hash from the provider, so it
passes it in. The hash then flows automatically into the feed's JSON, and the UI renders a
`view tx` link with `explorerTxUrl(network, txHash)`.

**Why this matters for trust:** the wall lets you type any **author** name - it is free text and
**spoofable** (anyone, especially via the command line, can post as "Satoshi"). The **transaction**,
by contrast, is real and verifiable: the explorer shows exactly what hit the chain. So we show the
name as a *claim* and the tx link as the *proof*. (Chapter 08 goes further and surfaces the signing
address as a verifiable identity.)

## 5. Dark mode without the "flash"

A theme switch has one classic bug: the page loads **light**, then a script runs and flips it
**dark** - a jarring white flash. We avoid it in three parts:

1. **Colours as variables** (`app/globals.css`): instead of hard-coding `#fff`/`#000` everywhere, we
   define tokens like `--bg`, `--fg`, `--muted`, and give `:root[data-theme="dark"]` darker values.
   Every style reads `var(--bg)` etc.
   *Analogy:* two labelled paint palettes; flipping the `data-theme` label repaints the whole page.
2. **Decide before paint** (`app/layout.tsx`): a tiny inline script runs in `<head>` **before** the
   body renders. It reads a saved choice (`localStorage`) or the OS preference
   (`prefers-color-scheme`) and sets `data-theme` on `<html>` immediately - so the first paint is
   already correct, no flash.
3. **The toggle** (`app/page.tsx`): a button flips the theme, updates `data-theme`, and saves the
   choice. The decision logic itself (`resolveInitialTheme`, `nextTheme`) lives in `lib.ts` and is
   unit-tested.

## 6. The rest of the polish

- **Network label** in the header (`network: preprod`) so visitors know which network to set their
  wallet to.
- **Byte counter** under the message box (`123 / 4096 bytes`), turning red and disabling Post when
  over the backend cap. The `4096` is a shared constant that mirrors `wall.max-message-bytes`.
- **"time ago"** timestamps in the feed, with the exact time on hover (`title`).
- **Friendly empty state** - "No posts yet - be the first" when online, or a hint to paste a
  Blockfrost key when offline.
- We pulled the feed rendering into its own `FeedList` component - a **presentational** component
  with no hooks or network, which makes it easy to test with RTL:
```tsx
render(<FeedList posts={[post()]} network="preprod" nowMs={now} offline={false} />);
expect(screen.getByRole("link", { name: /view tx/i }))
  .toHaveAttribute("href", "https://preprod.cardanoscan.io/transaction/deadbeef");
```

## 7. Running and testing it
```bash
# backend (unchanged, but now WallPost carries txHash):
./gradlew spotlessApply test          # 27 tests

# UI:
cd ui
npm test                              # 19 tests (lib + FeedList)
npm run typecheck                     # tsc --noEmit
npm run build                         # static export still succeeds
npm run dev                           # try the theme toggle, byte counter, etc. locally
```

## 8. What to notice / common mistakes
- **Test the logic, not the framework.** Pull pure functions out of components; test those directly.
  Reserve component (RTL) tests for "does this render right", using a presentational component.
- **Pass the clock in.** A function that calls `Date.now()` internally is hard to test. Take `nowMs`
  as an argument and the test can pin time.
- **Bytes, not characters.** A 64-byte metadata limit is not 64 characters once you use accents or
  emoji. Measure with `TextEncoder`.
- **Theme flash** comes from deciding the theme *after* first paint. Decide in the `<head>`, before
  the body renders.
- **Do not trust `public/` prefixing.** On project Pages the site lives under `/memory-wall`; the
  theme init touches only `document.documentElement`, and `config.js` is loaded with the base-path
  prefix (from Chapter 06 / the deploy work) - keep those in mind when adding new public assets.
- **Author is a claim, the tx is proof.** Never present the free-text author as identity.

## Glossary (Chapter 07)
- **Vitest** - a fast test runner for TS/JS projects (the JUnit of this UI).
- **React Testing Library (RTL)** - renders components and lets tests query them like a user does.
- **jsdom** - a fake browser (DOM) implemented in Node, so component tests run without a real browser.
- **Pure function** - same inputs always give the same output, with no side effects; trivially
  testable.
- **CSS variable (custom property)** - a named value like `var(--bg)` you can swap in one place to
  restyle everything.
- **`prefers-color-scheme`** - a browser signal for whether the user's OS is set to light or dark.
- **Flash of wrong theme (FOUC-style)** - the brief wrong-colour flash when the theme is applied
  after the first paint.
- **Transaction hash (txHash)** - the unique id of an on-chain transaction; the key to its explorer
  page.
- **Presentational component** - a component that just renders its props (no data fetching/state),
  which makes it easy to test.
