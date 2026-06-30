"use client";

import { useEffect, useState } from "react";

type Post = { author: string; message: string; timestamp: string };

export default function Home() {
  const [wallets, setWallets] = useState<string[]>([]);
  const [wallet, setWallet] = useState<string>("");
  const [author, setAuthor] = useState<string>("");
  const [message, setMessage] = useState<string>("");
  const [status, setStatus] = useState<string>("");
  const [feed, setFeed] = useState<Post[]>([]);

  useEffect(() => {
    const cardano = (window as any).cardano ?? {};
    setWallets(
      Object.keys(cardano).filter(
        (k) => cardano[k] && typeof cardano[k].enable === "function",
      ),
    );
    void loadFeed();
  }, []);

  async function loadFeed() {
    const res = await fetch("/api/feed");
    if (res.ok) {
      setFeed((await res.json()) as Post[]);
    }
  }

  async function post() {
    try {
      const cardano = (window as any).cardano;
      if (!wallet || !cardano?.[wallet]) {
        setStatus("Pick a wallet first.");
        return;
      }
      setStatus("Connecting wallet...");
      const api = await cardano[wallet].enable();
      const address: string = await api.getChangeAddress();

      setStatus("Building transaction...");
      const built = await fetch("/api/posts/build", {
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
      const submitted = await fetch("/api/posts/submit", {
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

  return (
    <main>
      <h1>Memory Wall</h1>
      <p>Post a permanent message to Cardano. Your wallet signs; the message lives on-chain forever.</p>

      <section style={{ display: "grid", gap: 8, marginBottom: 24 }}>
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
        <textarea
          placeholder="your message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
        />
        <button onClick={() => void post()} disabled={!message.trim()}>
          Post to the wall
        </button>
        {status && <p>{status}</p>}
      </section>

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
