import { describe, it, expect, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { FeedList } from "./FeedList";
import type { Post } from "./lib";

afterEach(cleanup);

const now = Date.parse("2026-07-13T12:00:00Z");
const post = (over: Partial<Post> = {}): Post => ({
  author: "Ada",
  message: "gm cardano",
  timestamp: "2026-07-13T09:00:00Z",
  txHash: "deadbeef",
  ...over,
});

describe("FeedList", () => {
  it("shows the 'be the first' empty state when online with no posts", () => {
    render(<FeedList posts={[]} network="preprod" nowMs={now} offline={false} />);
    expect(screen.getByText(/be the first/i)).toBeInTheDocument();
  });

  it("shows the offline empty state when offline with no posts", () => {
    render(<FeedList posts={[]} network="preprod" nowMs={now} offline={true} />);
    expect(screen.getByText(/paste a blockfrost key/i)).toBeInTheDocument();
  });

  it("renders a post with author, message, relative time, and an explorer link", () => {
    render(<FeedList posts={[post()]} network="preprod" nowMs={now} offline={false} />);
    expect(screen.getByText("Ada")).toBeInTheDocument();
    expect(screen.getByText("gm cardano")).toBeInTheDocument();
    expect(screen.getByText("3h ago")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: /view tx/i });
    expect(link).toHaveAttribute("href", "https://preprod.cardanoscan.io/transaction/deadbeef");
  });

  it("falls back to 'anon' and omits the tx link when there is no author or tx hash", () => {
    render(<FeedList posts={[post({ author: "", txHash: "" })]} network="preprod" nowMs={now} offline={false} />);
    expect(screen.getByText("anon")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /view tx/i })).toBeNull();
  });
});
