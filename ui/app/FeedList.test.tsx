import { describe, it, expect, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { FeedList } from "./FeedList";
import type { Post } from "./lib";

afterEach(cleanup);

const now = Date.parse("2026-07-13T12:00:00Z");
const ADDR = "addr_test1qpw0djgj0x59ngrjvqthn7enhvruxnsavsw5th63la3mjelz6aa7";
const post = (over: Partial<Post> = {}): Post => ({
  author: "Ada",
  message: "gm cardano",
  timestamp: "2026-07-13T09:00:00Z",
  txHash: "deadbeef",
  address: ADDR,
  tipLovelace: 0,
  pinned: false,
  color: "",
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

  it("shows the name as 'claimed' and the address as a 'verified' explorer link", () => {
    render(<FeedList posts={[post()]} network="preprod" nowMs={now} offline={false} />);
    expect(screen.getByText("(claimed)")).toBeInTheDocument();
    expect(screen.getByText("verified")).toBeInTheDocument();
    const addrLink = screen.getByRole("link", { name: "addr_test1qp...lz6aa7" });
    expect(addrLink).toHaveAttribute("href", `https://preprod.cardanoscan.io/address/${ADDR}`);
    expect(addrLink).toHaveAttribute("title", ADDR); // full address on hover
  });

  it("marks a pinned post with a PINNED badge and its tip", () => {
    render(
      <FeedList
        posts={[post({ pinned: true, tipLovelace: 5_000_000 })]}
        network="preprod"
        nowMs={now}
        offline={false}
      />,
    );
    expect(screen.getByText(/PINNED - 5 ADA/)).toBeInTheDocument();
  });

  it("falls back to 'anon' and omits tx/address links when there is no author, tx, or address", () => {
    render(
      <FeedList
        posts={[post({ author: "", txHash: "", address: "" })]}
        network="preprod"
        nowMs={now}
        offline={false}
      />,
    );
    expect(screen.getByText("anon")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /view tx/i })).toBeNull();
    expect(screen.queryByText("verified")).toBeNull();
  });
});
