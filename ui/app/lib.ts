// Pure helpers for the Memory Wall UI. Kept out of the React component so they are easy to unit-test
// (see lib.test.ts) and reuse. No DOM or network access here.

export type Post = {
  author: string;
  message: string;
  timestamp: string;
  txHash: string;
  address: string;
  tipLovelace: number;
  pinned: boolean;
};

/** Fee/pin tier config the backend exposes at /api/config so the UI can render and explain it. */
export type WallConfig = {
  feeEnabled: boolean;
  minFeeLovelace: number;
  pinFeeLovelace: number;
  maxPinned: number;
  pinDurationSeconds: number;
};

/** Lovelace -> ADA string (1 ADA = 1,000,000 lovelace), trimming trailing zeros. */
export function lovelaceToAda(lovelace: number): string {
  const ada = lovelace / 1_000_000;
  return Number.isInteger(ada) ? String(ada) : ada.toFixed(6).replace(/0+$/, "").replace(/\.$/, "");
}

/** ADA -> lovelace (rounded), for building a tip amount from a user's ADA input. */
export function adaToLovelace(ada: number): number {
  return Math.round(ada * 1_000_000);
}

/** Mirrors the backend default cap (wall.max-message-bytes) so the UI can warn before posting. */
export const MAX_MESSAGE_BYTES = 4096;

/** UTF-8 byte length of a string - the same unit Cardano metadata and the backend measure in. */
export function byteLength(s: string): number {
  return new TextEncoder().encode(s).length;
}

/** Cardanoscan host for the wall's network (defaults to preprod for anything unknown). */
function cardanoscanHost(network: string): string {
  return network === "mainnet"
    ? "cardanoscan.io"
    : network === "preview"
      ? "preview.cardanoscan.io"
      : "preprod.cardanoscan.io";
}

/** Cardanoscan transaction URL for the wall's network. */
export function explorerTxUrl(network: string, txHash: string): string {
  return `https://${cardanoscanHost(network)}/transaction/${txHash}`;
}

/** Cardanoscan address URL for the wall's network. */
export function explorerAddrUrl(network: string, address: string): string {
  return `https://${cardanoscanHost(network)}/address/${address}`;
}

/** Shorten a long address for display, e.g. "addr_test1qpw...z6aa7". */
export function shortenAddress(addr: string, head = 12, tail = 6): string {
  if (!addr || addr.length <= head + tail + 3) return addr;
  return `${addr.slice(0, head)}...${addr.slice(-tail)}`;
}

/**
 * Human "time ago" for an ISO-8601 timestamp, relative to nowMs (passed in so this stays a pure,
 * deterministic function). Falls back to the raw string if it cannot be parsed.
 */
export function relativeTime(iso: string, nowMs: number): string {
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return iso;
  const secs = Math.floor((nowMs - then) / 1000);
  if (secs < 0) return "just now";
  if (secs < 60) return "just now";
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(then).toISOString().slice(0, 10); // older: show the date (YYYY-MM-DD)
}

/**
 * Rebuild a Post from a Blockfrost metadata row (used by the read-from-chain fallback). Mirrors the
 * backend parser: author is `a`, message is `m` (a string or an array of 64-byte chunks), timestamp
 * is `ts`. Returns null for anything malformed.
 */
export function rowToPost(row: { tx_hash?: unknown; json_metadata?: unknown }): Post | null {
  const j = row?.json_metadata as Record<string, unknown> | undefined;
  if (!j || typeof j !== "object") return null;
  const author = typeof j.a === "string" ? j.a : "";
  const message = Array.isArray(j.m)
    ? (j.m as unknown[]).join("")
    : typeof j.m === "string"
      ? j.m
      : "";
  const timestamp = typeof j.ts === "string" ? j.ts : "";
  const txHash = typeof row?.tx_hash === "string" ? row.tx_hash : "";
  if (!message || !timestamp) return null;
  // The chain read-fallback has no cheap way to fetch the payer address or tip, so those stay empty
  // here (the UI simply omits the verified chip and pin styling); the backend feed fills them in.
  return { author, message, timestamp, txHash, address: "", tipLovelace: 0, pinned: false };
}

/**
 * Filter the loaded feed by a case-insensitive substring over author + message. An empty/blank query
 * returns all posts. NOTE: this only searches the posts already loaded (the recent window), not the
 * full on-chain history - see docs/BACKLOG.md (full-history search needs an indexer).
 */
export function filterPosts(posts: Post[], query: string): Post[] {
  const q = query.trim().toLowerCase();
  if (!q) return posts;
  return posts.filter((p) => (p.author + " " + p.message).toLowerCase().includes(q));
}

// --- theme (light/dark) ---------------------------------------------------------------------------

export type Theme = "light" | "dark";

/** Initial theme: a saved choice wins; otherwise follow the OS preference. */
export function resolveInitialTheme(stored: string | null, prefersDark: boolean): Theme {
  if (stored === "light" || stored === "dark") return stored;
  return prefersDark ? "dark" : "light";
}

/** The other theme (for a toggle button). */
export function nextTheme(t: Theme): Theme {
  return t === "dark" ? "light" : "dark";
}
