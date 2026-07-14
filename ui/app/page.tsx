"use client";

import { useEffect, useState, type CSSProperties } from "react";
import {
  adaToLovelace,
  byteCountColor,
  byteLength,
  filterPosts,
  lovelaceToAda,
  pinColorBg,
  MAX_AUTHOR_BYTES,
  MAX_MESSAGE_BYTES,
  nextTheme,
  resolveInitialTheme,
  rowToPost,
  type Post,
  type Theme,
  type WallConfig,
} from "./lib";
import { FeedList } from "./FeedList";

type Health = "checking" | "online" | "offline";

const PAGE_SIZE = 20; // posts fetched per "page"; a full page implies there may be more

// UI accent swatches (global theme accent - separate from the per-post pin colour palette).
const ACCENTS = [
  { name: "green", hex: "#7ee787", rgb: "126, 231, 135" },
  { name: "yellow", hex: "#d8a657", rgb: "216, 166, 87" },
  { name: "blue", hex: "#4d9fff", rgb: "77, 159, 255" },
  { name: "pink", hex: "#ff6ac1", rgb: "255, 106, 193" },
];

// Runtime config (from public/config.js) - set after deploy, no rebuild needed.
const cfg = () =>
  typeof window !== "undefined" ? (window as unknown as Record<string, string>) : {};
const apiBase = (): string => cfg().__WALL_API__ || ""; // "" = same-origin (dev proxy)
const network = (): string => cfg().__WALL_NETWORK__ || "preprod";

export default function Home() {
  const [wallets, setWallets] = useState<string[]>([]);
  const [wallet, setWallet] = useState("");
  const [author, setAuthor] = useState("");
  const [message, setMessage] = useState("");
  const [status, setStatus] = useState("");
  const [feed, setFeed] = useState<Post[]>([]);
  const [health, setHealth] = useState<Health>("checking");
  const [bfKey, setBfKey] = useState("");
  const [theme, setTheme] = useState<Theme>("light");
  const [accent, setAccent] = useState("#7ee787");
  const [query, setQuery] = useState("");
  const [config, setConfig] = useState<WallConfig | null>(null);
  const [tipAda, setTipAda] = useState("");
  const [pinColor, setPinColor] = useState("");
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [searchResults, setSearchResults] = useState<Post[]>([]);
  const [searchPage, setSearchPage] = useState(1);
  const [searchHasMore, setSearchHasMore] = useState(false);

  // Fetch the fee/pin tier config once, so we know whether to show the tip field + rules.
  async function loadConfig() {
    try {
      const r = await fetch(`${apiBase()}/api/config`);
      if (r.ok) {
        const c = (await r.json()) as WallConfig;
        setConfig(c);
        if (c.feeEnabled) setTipAda(lovelaceToAda(c.minFeeLovelace)); // prefill the minimum
      }
    } catch {
      /* config is optional; posting still works with the tier off */
    }
  }

  // Poll the backend's health so we can show an online/offline light and disable posting when down.
  async function probe(): Promise<boolean> {
    try {
      const r = await fetch(`${apiBase()}/api/health`, { cache: "no-store" });
      setHealth(r.ok ? "online" : "offline");
      return r.ok;
    } catch {
      setHealth("offline");
      return false;
    }
  }

  // Fetch one page of the feed from the backend (null on failure).
  async function fetchFeedPage(p: number): Promise<Post[] | null> {
    try {
      const res = await fetch(`${apiBase()}/api/feed?limit=${PAGE_SIZE}&page=${p}`);
      if (res.ok) return (await res.json()) as Post[];
    } catch {
      /* ignore */
    }
    return null;
  }

  // Load (reset to) the first page: from the backend when up, else directly from the chain.
  async function loadFeed() {
    const online = await probe();
    if (online) {
      const posts = await fetchFeedPage(1);
      if (posts) {
        setFeed(posts);
        setPage(1);
        setHasMore(posts.length >= PAGE_SIZE); // a full page implies there may be more
      }
      return;
    }
    await loadFeedFromChain();
  }

  // "Load more": fetch the next page and append it (de-duped by tx hash).
  async function loadMore() {
    const next = page + 1;
    const posts = await fetchFeedPage(next);
    if (posts) {
      setFeed((prev) => {
        const seen = new Set(prev.map((p) => p.txHash).filter(Boolean));
        return [...prev, ...posts.filter((p) => !p.txHash || !seen.has(p.txHash))];
      });
      setPage(next);
      setHasMore(posts.length >= PAGE_SIZE);
    }
  }

  // Full-history search against the backend index (page 1 resets; higher pages append, de-duped).
  async function runSearch(q: string, p: number) {
    try {
      const res = await fetch(
        `${apiBase()}/api/search?q=${encodeURIComponent(q)}&limit=${PAGE_SIZE}&page=${p}`,
      );
      if (!res.ok) return;
      const posts = (await res.json()) as Post[];
      setSearchResults((prev) =>
        p === 1
          ? posts
          : [...prev, ...posts.filter((x) => !prev.some((y) => y.txHash === x.txHash))],
      );
      setSearchPage(p);
      setSearchHasMore(posts.length >= PAGE_SIZE);
    } catch {
      /* ignore - search is best-effort */
    }
  }

  // Read-only fallback: pull posts straight from Blockfrost when the backend is offline.
  async function loadFeedFromChain() {
    const key = bfKey.trim();
    if (!key) return; // no key -> nothing we can do while offline
    try {
      const base = `https://cardano-${network()}.blockfrost.io/api/v0`;
      const res = await fetch(`${base}/metadata/txs/labels/1719?order=desc&count=20`, {
        headers: { project_id: key },
      });
      if (!res.ok) {
        setStatus("Chain read failed: HTTP " + res.status);
        return;
      }
      const rows = (await res.json()) as Array<{ tx_hash?: unknown; json_metadata?: unknown }>;
      const posts = rows
        .map(rowToPost)
        .filter((p): p is Post => p !== null)
        .sort((a, b) => b.timestamp.localeCompare(a.timestamp));
      setFeed(posts);
      setStatus("Loaded " + posts.length + " posts directly from the chain (read-only).");
    } catch (e: unknown) {
      setStatus("Chain read error: " + (e instanceof Error ? e.message : String(e)));
    }
  }

  useEffect(() => {
    // Adopt the theme the layout script already applied to <html> (falls back if it did not run).
    const applied = document.documentElement.getAttribute("data-theme");
    const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
    setTheme(resolveInitialTheme(applied ?? localStorage.getItem("wall-theme"), prefersDark));
    const savedAccent = localStorage.getItem("wall-accent"); // "#hex|r, g, b"
    if (savedAccent) setAccent(savedAccent.split("|")[0]);

    const cardano =
      (window as unknown as { cardano?: Record<string, { enable?: unknown }> }).cardano ?? {};
    setWallets(Object.keys(cardano).filter((k) => typeof cardano[k]?.enable === "function"));
    void loadConfig();
    void loadFeed();
    const id = setInterval(() => void probe(), 30_000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Debounced full-history search: when there's a query and the backend is up, query /api/search.
  useEffect(() => {
    if (!query.trim() || health !== "online") {
      setSearchResults([]);
      setSearchHasMore(false);
      return;
    }
    const q = query.trim();
    const id = setTimeout(() => void runSearch(q, 1), 300);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, health]);

  function toggleTheme() {
    const t = nextTheme(theme);
    setTheme(t);
    document.documentElement.setAttribute("data-theme", t);
    try {
      localStorage.setItem("wall-theme", t);
    } catch {
      /* ignore storage errors (private mode) */
    }
  }

  function pickAccent(hex: string, rgb: string) {
    const root = document.documentElement;
    root.style.setProperty("--accent", hex);
    root.style.setProperty("--accent-rgb", rgb);
    setAccent(hex);
    try {
      localStorage.setItem("wall-accent", hex + "|" + rgb);
    } catch {
      /* ignore storage errors */
    }
  }

  async function post() {
    try {
      if (health !== "online") {
        setStatus("The wall is offline right now - posting is unavailable.");
        return;
      }
      const cardano = (
        window as unknown as { cardano?: Record<string, { enable: () => Promise<any> }> }
      ).cardano;
      if (!wallet || !cardano?.[wallet]) {
        setStatus("Pick a wallet first.");
        return;
      }
      setStatus("Connecting wallet...");
      const api = await cardano[wallet].enable();
      const address: string = await api.getChangeAddress();

      setStatus("Building transaction...");
      const body: Record<string, unknown> = { address, author, message };
      if (config?.feeEnabled) body.tipLovelace = adaToLovelace(Number(tipAda) || 0);
      if (config?.feeEnabled && pinColor) body.color = pinColor;
      const built = await fetch(`${apiBase()}/api/posts/build`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!built.ok) {
        setStatus("Build failed: " + (await built.text()));
        return;
      }
      const { txCbor } = (await built.json()) as { txCbor: string };

      setStatus("Waiting for wallet signature...");
      const witness: string = await api.signTx(txCbor, true);

      setStatus("Submitting...");
      const submitted = await fetch(`${apiBase()}/api/posts/submit`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ txCbor, witness }),
      });
      if (!submitted.ok) {
        setStatus("Submit failed: " + (await submitted.text()));
        return;
      }
      const { txHash } = (await submitted.json()) as { txHash: string };
      setStatus("Posted! tx " + txHash);
      setMessage("");
      setTimeout(() => void loadFeed(), 2000);
    } catch (e: unknown) {
      setStatus("Error: " + (e instanceof Error ? e.message : String(e)));
    }
  }

  const dotColor = health === "online" ? "#0a7d2e" : health === "offline" ? "#d24" : "#999";
  const offline = health === "offline";
  const bytes = byteLength(message);
  const overLimit = bytes > MAX_MESSAGE_BYTES;
  const authorBytes = byteLength(author);
  const authorOver = authorBytes > MAX_AUTHOR_BYTES;
  const nowMs = Date.now();
  const online = health === "online";
  // Online + a query -> full-history results from /api/search; otherwise the loaded feed (filtered
  // client-side when offline). "Load more" pages the feed, or the search results in search mode.
  const searchMode = query.trim().length > 0 && online;
  const visible = searchMode ? searchResults : filterPosts(feed, query);
  const showLoadMore = online && (searchMode ? searchHasMore : !query.trim() && hasMore);
  const feeOn = config?.feeEnabled ?? false;
  const tipLov = adaToLovelace(Number(tipAda) || 0);
  const belowMin = feeOn && !!config && tipLov < config.minFeeLovelace;
  const willPin = feeOn && !!config && config.pinFeeLovelace > 0 && tipLov >= config.pinFeeLovelace;
  const pinDays = config ? Math.max(1, Math.round(config.pinDurationSeconds / 86400)) : 0;

  return (
    <main>
      <div id="controls">
        <div id="accent" role="group" aria-label="Accent colour">
          {ACCENTS.map((a) => (
            <button
              key={a.hex}
              className="sw"
              type="button"
              title={a.name}
              aria-label={`${a.name} accent`}
              aria-pressed={accent === a.hex}
              style={{ ["--sw"]: a.hex } as CSSProperties}
              onClick={() => pickAccent(a.hex, a.rgb)}
            />
          ))}
        </div>
        <button
          id="theme"
          type="button"
          aria-label="Toggle dark mode"
          title="Toggle dark / light mode"
          onClick={toggleTheme}
        >
          <svg
            className="sun"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
          </svg>
          <svg
            className="moon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
          </svg>
        </button>
      </div>
      <h1>Memory Wall</h1>
      <p style={{ marginTop: 0 }}>
        Post a permanent message to Cardano. Your wallet signs; the message lives on-chain forever.{" "}
        <span style={{ color: "var(--muted)", fontSize: 13 }}>(network: {network()})</span>
      </p>

      <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "8px 0" }}>
        <span
          style={{
            width: 10,
            height: 10,
            borderRadius: "50%",
            background: dotColor,
            display: "inline-block",
          }}
        />
        <span style={{ fontSize: 13 }}>
          wall server: {health === "checking" ? "checking..." : health}
        </span>
        <button onClick={() => void loadFeed()} style={{ marginLeft: "auto", fontSize: 12 }}>
          Refresh
        </button>
      </div>

      {offline && (
        <div
          style={{
            border: "1px solid var(--danger)",
            background: "var(--danger-bg)",
            borderRadius: 8,
            padding: "8px 12px",
            fontSize: 14,
          }}
        >
          The wall server is offline - new posts are unavailable. You can still read existing posts:
          paste a free Blockfrost project id below to load them straight from the chain.
        </div>
      )}

      <section style={{ display: "grid", gap: 8, marginBottom: 24, marginTop: 12 }}>
        <select value={wallet} onChange={(e) => setWallet(e.target.value)}>
          <option value="">Select a wallet...</option>
          {wallets.map((w) => (
            <option key={w} value={w}>
              {w}
            </option>
          ))}
        </select>
        <input
          placeholder="name (optional)"
          value={author}
          onChange={(e) => setAuthor(e.target.value)}
        />
        <div
          style={{
            fontSize: 12,
            color: byteCountColor(authorBytes, MAX_AUTHOR_BYTES),
            textAlign: "right",
          }}
        >
          {authorBytes} / {MAX_AUTHOR_BYTES} bytes{authorOver ? " - name too long" : ""}
        </div>
        <textarea
          placeholder="your message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
        />
        <div
          style={{
            fontSize: 12,
            color: byteCountColor(bytes, MAX_MESSAGE_BYTES),
            textAlign: "right",
          }}
        >
          {bytes} / {MAX_MESSAGE_BYTES} bytes{overLimit ? " - too long" : ""}
        </div>

        {feeOn && config && (
          <div
            style={{
              border: "1px solid var(--border)",
              borderRadius: 8,
              padding: "8px 12px",
              display: "grid",
              gap: 6,
            }}
          >
            <label style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 8 }}>
              Tip (ADA):
              <input
                type="number"
                min={lovelaceToAda(config.minFeeLovelace)}
                step="0.1"
                value={tipAda}
                onChange={(e) => setTipAda(e.target.value)}
                style={{ width: 120 }}
              />
              {willPin ? (
                <span style={{ color: "var(--pin-badge)", fontWeight: 700, fontSize: 12 }}>
                  will pin
                </span>
              ) : null}
            </label>
            <div style={{ fontSize: 12, color: belowMin ? "var(--danger)" : "var(--muted)" }}>
              <strong>Pinning rules:</strong> posting costs at least{" "}
              {lovelaceToAda(config.minFeeLovelace)} ADA (a tip to the wall). Tip{" "}
              {lovelaceToAda(config.pinFeeLovelace)} ADA or more to <strong>pin</strong> your post
              to the top. At most {config.maxPinned} posts are pinned at once - the highest tips win
              the slots, a pin lasts up to {pinDays} day{pinDays === 1 ? "" : "s"}, and a bigger tip
              can bump a smaller one. The tip is paid on-chain to the wall.
              {belowMin ? " (Your tip is below the minimum.)" : ""}
            </div>
            {willPin && config.palette.length > 0 && (
              <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12 }}>
                <span style={{ color: "var(--muted)" }}>pin colour:</span>
                {config.palette.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setPinColor(c)}
                    title={c}
                    aria-label={`pin colour ${c}`}
                    style={{
                      width: 20,
                      height: 20,
                      borderRadius: "50%",
                      background: pinColorBg(c),
                      border: pinColor === c ? "2px solid var(--fg)" : "1px solid var(--border)",
                      padding: 0,
                    }}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        <button
          onClick={() => void post()}
          disabled={!message.trim() || overLimit || authorOver || belowMin || health !== "online"}
        >
          {offline ? "Posting unavailable (server offline)" : "Post to the wall"}
        </button>
        {status && <p>{status}</p>}
      </section>

      {offline && (
        <details style={{ marginBottom: 16 }}>
          <summary style={{ cursor: "pointer", fontSize: 13 }}>
            Read posts directly from the chain (optional Blockfrost key)
          </summary>
          <p style={{ fontSize: 13, opacity: 0.8 }}>
            Used automatically when the server is offline. Your key is your own, read-only, and
            never sent anywhere except Blockfrost from your browser. Network: {network()}.
          </p>
          <div style={{ display: "flex", gap: 8 }}>
            <input
              style={{ flex: 1 }}
              placeholder={network() + "XXXXXXXXXXXXXXXX"}
              value={bfKey}
              onChange={(e) => setBfKey(e.target.value)}
            />
            <button onClick={() => void loadFeedFromChain()} disabled={!bfKey.trim()}>
              Load from chain
            </button>
          </div>
        </details>
      )}

      <h2>The wall</h2>
      {(online || feed.length > 0) && (
        <div style={{ margin: "4px 0 12px" }}>
          <input
            style={{ width: "100%" }}
            placeholder="Search posts (author or message)..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {query.trim() && (
            <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 4 }}>
              {searchMode
                ? `Searching all posts - ${visible.length} result${visible.length === 1 ? "" : "s"}${searchHasMore ? "+" : ""}.`
                : `${visible.length} of ${feed.length} loaded posts match (offline: only the loaded window).`}
            </div>
          )}
        </div>
      )}
      {query.trim() && visible.length === 0 ? (
        <p style={{ color: "var(--muted)" }}>No posts match &quot;{query.trim()}&quot;.</p>
      ) : (
        <FeedList posts={visible} network={network()} nowMs={nowMs} offline={offline} />
      )}
      {showLoadMore && (
        <div style={{ textAlign: "center", marginTop: 12 }}>
          <button
            onClick={() =>
              searchMode ? void runSearch(query.trim(), searchPage + 1) : void loadMore()
            }
          >
            Load more
          </button>
        </div>
      )}
    </main>
  );
}
