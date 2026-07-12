"use client";

import { useEffect, useState } from "react";

type Post = { author: string; message: string; timestamp: string };
type Health = "checking" | "online" | "offline";

// Runtime config (from public/config.js) - set after deploy, no rebuild needed.
const cfg = () => (typeof window !== "undefined" ? (window as unknown as Record<string, string>) : {});
const apiBase = (): string => cfg().__WALL_API__ || ""; // "" = same-origin (dev proxy)
const network = (): string => cfg().__WALL_NETWORK__ || "preprod";

/** Rebuild a Post from a Blockfrost json_metadata object (mirrors the backend's parser). */
function rowToPost(row: { json_metadata?: unknown }): Post | null {
  const j = row?.json_metadata as Record<string, unknown> | undefined;
  if (!j || typeof j !== "object") return null;
  const author = typeof j.a === "string" ? j.a : "";
  const message = Array.isArray(j.m)
    ? (j.m as unknown[]).join("")
    : typeof j.m === "string"
      ? j.m
      : "";
  const timestamp = typeof j.ts === "string" ? j.ts : "";
  if (!message || !timestamp) return null;
  return { author, message, timestamp };
}

export default function Home() {
  const [wallets, setWallets] = useState<string[]>([]);
  const [wallet, setWallet] = useState("");
  const [author, setAuthor] = useState("");
  const [message, setMessage] = useState("");
  const [status, setStatus] = useState("");
  const [feed, setFeed] = useState<Post[]>([]);
  const [health, setHealth] = useState<Health>("checking");
  const [bfKey, setBfKey] = useState("");

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
      const rows = (await res.json()) as Array<{ json_metadata?: unknown }>;
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
    const cardano = (window as unknown as { cardano?: Record<string, { enable?: unknown }> }).cardano ?? {};
    setWallets(Object.keys(cardano).filter((k) => typeof cardano[k]?.enable === "function"));
    void loadFeed();
    const id = setInterval(() => void probe(), 30_000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
      const built = await fetch(`${apiBase()}/api/posts/build`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ address, author, message }),
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

  return (
    <main>
      <h1>Memory Wall</h1>
      <p>Post a permanent message to Cardano. Your wallet signs; the message lives on-chain forever.</p>

      <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "8px 0" }}>
        <span style={{ width: 10, height: 10, borderRadius: "50%", background: dotColor, display: "inline-block" }} />
        <span style={{ fontSize: 13 }}>
          wall server: {health === "checking" ? "checking..." : health}
        </span>
        <button onClick={() => void loadFeed()} style={{ marginLeft: "auto", fontSize: 12 }}>
          Refresh
        </button>
      </div>

      {offline && (
        <div style={{ border: "1px solid #b4232388", background: "#b4232322", borderRadius: 8, padding: "8px 12px", fontSize: 14 }}>
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
        <button onClick={() => void post()} disabled={!message.trim() || health !== "online"}>
          {offline ? "Posting unavailable (server offline)" : "Post to the wall"}
        </button>
        {status && <p>{status}</p>}
      </section>

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

      <h2>The wall</h2>
      <ul style={{ listStyle: "none", padding: 0 }}>
        {feed.map((p, i) => (
          <li key={i} style={{ borderTop: "1px solid #ddd", padding: "8px 0" }}>
            <strong>{p.author || "anon"}</strong>{" "}
            <span style={{ color: "#888", fontSize: 12 }}>{p.timestamp}</span>
            <div>{p.message}</div>
          </li>
        ))}
      </ul>
    </main>
  );
}
