import {
  explorerAddrUrl,
  explorerTxUrl,
  lovelaceToAda,
  relativeTime,
  shortenAddress,
  type Post,
} from "./lib";

// Presentational feed: given the posts, render them (or a friendly empty state). Kept free of hooks,
// fetch, and window access so it is trivial to unit-test with React Testing Library.
export function FeedList({
  posts,
  network,
  nowMs,
  offline,
}: {
  posts: Post[];
  network: string;
  nowMs: number;
  offline: boolean;
}) {
  if (posts.length === 0) {
    return (
      <p style={{ color: "var(--muted)" }}>
        {offline
          ? "No posts loaded. Paste a Blockfrost key above to read from the chain."
          : "No posts yet - be the first to write on the wall."}
      </p>
    );
  }
  return (
    <ul style={{ listStyle: "none", padding: 0 }}>
      {posts.map((p, i) => (
        <li
          key={p.txHash || i}
          style={
            p.pinned
              ? {
                  background: "var(--pin-bg)",
                  border: "1px solid var(--pin-border)",
                  borderRadius: 8,
                  padding: "8px 10px",
                  margin: "6px 0",
                }
              : { borderTop: "1px solid var(--border)", padding: "8px 0" }
          }
        >
          <div>
            {/* Pinned posts (verified on-chain tip) stand out and show the tip that pinned them. */}
            {p.pinned && (
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  background: "var(--pin-badge)",
                  color: "var(--pin-badge-fg)",
                  borderRadius: 4,
                  padding: "1px 6px",
                  marginRight: 6,
                }}
              >
                PINNED{p.tipLovelace > 0 ? ` - ${lovelaceToAda(p.tipLovelace)} ADA` : ""}
              </span>
            )}
            {/* The name is a self-reported claim; the address (when present) is read from the chain
                and provably signed the transaction, so it is the trustworthy identity. */}
            <strong>{p.author || "anon"}</strong>{" "}
            <span style={{ color: "var(--muted)", fontSize: 12 }}>(claimed)</span>{" "}
            {p.address && (
              <>
                <a
                  href={explorerAddrUrl(network, p.address)}
                  target="_blank"
                  rel="noreferrer"
                  title={p.address}
                  style={{
                    fontSize: 12,
                    background: "var(--chip-bg)",
                    border: "1px solid var(--border)",
                    borderRadius: 6,
                    padding: "1px 6px",
                    textDecoration: "none",
                  }}
                >
                  {shortenAddress(p.address)}
                </a>{" "}
                <span style={{ color: "var(--muted)", fontSize: 11 }}>verified</span>{" "}
              </>
            )}
            <span style={{ color: "var(--muted)", fontSize: 12 }} title={p.timestamp}>
              {relativeTime(p.timestamp, nowMs)}
            </span>
            {p.txHash && (
              <>
                {" "}
                <a
                  href={explorerTxUrl(network, p.txHash)}
                  target="_blank"
                  rel="noreferrer"
                  style={{ fontSize: 12 }}
                >
                  view tx
                </a>
              </>
            )}
          </div>
          <div>{p.message}</div>
        </li>
      ))}
    </ul>
  );
}
