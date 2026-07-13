import { explorerTxUrl, relativeTime, type Post } from "./lib";

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
        <li key={p.txHash || i} style={{ borderTop: "1px solid var(--border)", padding: "8px 0" }}>
          <strong>{p.author || "anon"}</strong>{" "}
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
          <div>{p.message}</div>
        </li>
      ))}
    </ul>
  );
}
