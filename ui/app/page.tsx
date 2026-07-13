"use client";

import { useEffect, useState } from "react";
import {
  adaToLovelace,
  byteLength,
  filterPosts,
  lovelaceToAda,
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

// Runtime config (from public/config.js) - set after deploy, no rebuild needed.
const cfg = () => (typeof window !== "undefined" ? (window as unknown as Record<string, string>) : {});
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
  const [query, setQuery] = useState("");
  const [config, setConfig] = useState<WallConfig | null>(null);
  const [tipAda, setTipAda] = useState("");

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

  // Load the feed: from the backend when it is up, otherwise directly from the chain (if a key is set).
  async function loadFeed() {
    const online = await probe();
    if (online) {
      try {
        const res = await fetch(`${apiBase()}/api/feed`);
        if (res.ok) setFeed((await res.json()) as Post[]);
      } catch {
        /* fall through to chain read below */
      }
      return;
    }
    await loadFeedFromChain();
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

    const cardano = (window as unknown as { cardano?: Record<string, { enable?: unknown }> }).cardano ?? {};
    setWallets(Object.keys(cardano).filter((k) => typeof cardano[k]?.enable === "function"));
    void loadConfig();
    void loadFeed();
    const id = setInterval(() => void probe(), 30_000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  async function post() {
    try {
      if (health !== "online") {
        setStatus("The wall is offline right now - posting is unavailable.");
        return;
      }
      const cardano = (window as unknown as { cardano?: Record<string, { enable: () => Promise<any> }> }).cardano;
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
  const nowMs = Date.now();
  const visible = filterPosts(feed, query);
  const feeOn = config?.feeEnabled ?? false;
  const tipLov = adaToLovelace(Number(tipAda) || 0);
  const belowMin = feeOn && !!config && tipLov < config.minFeeLovelace;
  const willPin = feeOn && !!config && config.pinFeeLovelace > 0 && tipLov >= config.pinFeeLovelace;
  const pinDays = config ? Math.max(1, Math.round(config.pinDurationSeconds / 86400)) : 0;

  return (
    <main>
      <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
        <h1 style={{ marginRight: "auto" }}>Memory Wall</h1>
        <span style={{ fontSize: 12, color: "var(--muted)" }}>network: {network()}</span>
        <button onClick={toggleTheme} aria-label="Toggle dark mode" style={{ fontSize: 12 }}>
          {theme === "dark" ? "Light mode" : "Dark mode"}
        </button>
      </div>
      <p>Post a permanent message to Cardano. Your wallet signs; the message lives on-chain forever.</p>

      <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "8px 0" }}>
        <span style={{ width: 10, height: 10, borderRadius: "50%", background: dotColor, display: "inline-block" }} />
        <span style={{ fontSize: 13 }}>wall server: {health === "checking" ? "checking..." : health}</span>
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
        <input placeholder="name (optional)" value={author} onChange={(e) => setAuthor(e.target.value)} />
        <textarea placeholder="your message" value={message} onChange={(e) => setMessage(e.target.value)} />
        <div style={{ fontSize: 12, color: overLimit ? "var(--danger)" : "var(--muted)", textAlign: "right" }}>
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
              {lovelaceToAda(config.pinFeeLovelace)} ADA or more to <strong>pin</strong> your post to
              the top. At most {config.maxPinned} posts are pinned at once - the highest tips win the
              slots, a pin lasts up to {pinDays} day{pinDays === 1 ? "" : "s"}, and a bigger tip can
              bump a smaller one. The tip is paid on-chain to the wall.
              {belowMin ? " (Your tip is below the minimum.)" : ""}
            </div>
          </div>
        )}

        <button
          onClick={() => void post()}
          disabled={!message.trim() || overLimit || belowMin || health !== "online"}
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
            Used automatically when the server is offline. Your key is your own, read-only, and never
            sent anywhere except Blockfrost from your browser. Network: {network()}.
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
      {feed.length > 0 && (
        <div style={{ margin: "4px 0 12px" }}>
          <input
            style={{ width: "100%" }}
            placeholder="Search the loaded posts (author or message)..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {query.trim() && (
            <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 4 }}>
              {visible.length} of {feed.length} loaded posts match. Searches only the loaded window,
              not the full history.
            </div>
          )}
        </div>
      )}
      {query.trim() && visible.length === 0 && feed.length > 0 ? (
        <p style={{ color: "var(--muted)" }}>No loaded posts match &quot;{query.trim()}&quot;.</p>
      ) : (
        <FeedList posts={visible} network={network()} nowMs={nowMs} offline={offline} />
      )}
    </main>
  );
}
