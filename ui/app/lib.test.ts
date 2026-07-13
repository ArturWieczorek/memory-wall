import { describe, it, expect } from "vitest";
import {
  byteLength,
  explorerTxUrl,
  explorerAddrUrl,
  shortenAddress,
  relativeTime,
  rowToPost,
  filterPosts,
  lovelaceToAda,
  adaToLovelace,
  pinColorBg,
  resolveInitialTheme,
  nextTheme,
  MAX_MESSAGE_BYTES,
  type Post,
} from "./lib";

const mkPost = (over: Partial<Post> = {}): Post => ({
  author: "Ada",
  message: "gm cardano",
  timestamp: "2026-07-13T12:00:00Z",
  txHash: "tx",
  address: "",
  tipLovelace: 0,
  pinned: false,
  color: "",
  ...over,
});

describe("byteLength", () => {
  it("counts ASCII as one byte each", () => {
    expect(byteLength("hello")).toBe(5);
    expect(byteLength("")).toBe(0);
  });
  it("counts multi-byte UTF-8 characters by their byte length", () => {
    expect(byteLength("e")).toBe(1);
    expect(byteLength(String.fromCharCode(0xe9))).toBe(2); // U+00E9 e-acute = 2 UTF-8 bytes
    expect(byteLength(String.fromCharCode(0x20ac))).toBe(3); // U+20AC euro sign = 3 UTF-8 bytes
  });
  it("exposes a cap that matches the backend default", () => {
    expect(MAX_MESSAGE_BYTES).toBe(4096);
  });
});

describe("explorerTxUrl", () => {
  const hash = "abc123";
  it("uses the preprod host by default and for preprod", () => {
    expect(explorerTxUrl("preprod", hash)).toBe(`https://preprod.cardanoscan.io/transaction/${hash}`);
    expect(explorerTxUrl("something-unknown", hash)).toBe(
      `https://preprod.cardanoscan.io/transaction/${hash}`,
    );
  });
  it("uses preview and mainnet hosts", () => {
    expect(explorerTxUrl("preview", hash)).toBe(`https://preview.cardanoscan.io/transaction/${hash}`);
    expect(explorerTxUrl("mainnet", hash)).toBe(`https://cardanoscan.io/transaction/${hash}`);
  });
});

describe("explorerAddrUrl", () => {
  it("builds a cardanoscan address URL for the network", () => {
    expect(explorerAddrUrl("preprod", "addr_test1qxyz")).toBe(
      "https://preprod.cardanoscan.io/address/addr_test1qxyz",
    );
    expect(explorerAddrUrl("mainnet", "addr1qxyz")).toBe(
      "https://cardanoscan.io/address/addr1qxyz",
    );
  });
});

describe("shortenAddress", () => {
  it("shortens a long address to head...tail", () => {
    const addr = "addr_test1qpw0djgj0x59ngrjvqthn7enhvruxnsavsw5th63la3mjelz6aa7";
    expect(shortenAddress(addr)).toBe("addr_test1qp...lz6aa7");
  });
  it("leaves a short address unchanged and tolerates empty input", () => {
    expect(shortenAddress("addr1short")).toBe("addr1short");
    expect(shortenAddress("")).toBe("");
  });
});

describe("relativeTime", () => {
  const now = Date.parse("2026-07-13T12:00:00Z");
  it("says 'just now' under a minute (and for future stamps)", () => {
    expect(relativeTime("2026-07-13T11:59:30Z", now)).toBe("just now");
    expect(relativeTime("2026-07-13T12:00:10Z", now)).toBe("just now");
  });
  it("reports minutes, hours, and days", () => {
    expect(relativeTime("2026-07-13T11:57:00Z", now)).toBe("3m ago");
    expect(relativeTime("2026-07-13T09:00:00Z", now)).toBe("3h ago");
    expect(relativeTime("2026-07-10T12:00:00Z", now)).toBe("3d ago");
  });
  it("shows a date for anything older than ~30 days", () => {
    expect(relativeTime("2026-05-01T12:00:00Z", now)).toBe("2026-05-01");
  });
  it("returns the raw string when unparseable", () => {
    expect(relativeTime("not-a-date", now)).toBe("not-a-date");
  });
});

describe("rowToPost", () => {
  it("parses author, a chunked message array, timestamp, and tx hash", () => {
    const post = rowToPost({
      tx_hash: "deadbeef",
      json_metadata: { a: "Ada", m: ["Hello, ", "world"], ts: "2026-07-13T12:00:00Z" },
    });
    expect(post).toEqual({
      author: "Ada",
      message: "Hello, world",
      timestamp: "2026-07-13T12:00:00Z",
      txHash: "deadbeef",
      address: "",
      tipLovelace: 0,
      pinned: false,
      color: "",
    });
  });
  it("accepts a plain string message and a missing tx hash (address stays empty here)", () => {
    const post = rowToPost({ json_metadata: { a: "", m: "hi", ts: "2026-07-13T12:00:00Z" } });
    expect(post).toEqual({
      author: "",
      message: "hi",
      timestamp: "2026-07-13T12:00:00Z",
      txHash: "",
      address: "",
      tipLovelace: 0,
      pinned: false,
      color: "",
    });
  });
  it("returns null for malformed rows", () => {
    expect(rowToPost({ json_metadata: { a: "x", ts: "2026-07-13T12:00:00Z" } })).toBeNull(); // no message
    expect(rowToPost({ json_metadata: { m: "hi" } })).toBeNull(); // no timestamp
    expect(rowToPost({ json_metadata: "nope" })).toBeNull(); // not an object
    expect(rowToPost({})).toBeNull(); // no metadata
  });
});

describe("filterPosts", () => {
  const posts = [
    mkPost({ author: "Ada", message: "gm cardano" }),
    mkPost({ author: "Bob", message: "hello world" }),
    mkPost({ author: "", message: "CARDANO rocks" }),
  ];
  it("returns all posts for an empty/blank query", () => {
    expect(filterPosts(posts, "")).toHaveLength(3);
    expect(filterPosts(posts, "   ")).toHaveLength(3);
  });
  it("matches case-insensitively across author and message", () => {
    expect(filterPosts(posts, "cardano").map((p) => p.author)).toEqual(["Ada", ""]);
    expect(filterPosts(posts, "BOB")).toHaveLength(1);
  });
  it("returns none when nothing matches", () => {
    expect(filterPosts(posts, "zzz")).toHaveLength(0);
  });
});

describe("ADA / lovelace helpers", () => {
  it("formats lovelace as ADA, trimming trailing zeros", () => {
    expect(lovelaceToAda(5_000_000)).toBe("5");
    expect(lovelaceToAda(1_500_000)).toBe("1.5");
    expect(lovelaceToAda(0)).toBe("0");
  });
  it("converts ADA to lovelace, rounding", () => {
    expect(adaToLovelace(5)).toBe(5_000_000);
    expect(adaToLovelace(1.5)).toBe(1_500_000);
    expect(adaToLovelace(0)).toBe(0);
  });
});

describe("pin colour", () => {
  it("maps a palette colour to its CSS var, else the default pin pastel", () => {
    expect(pinColorBg("mint")).toBe("var(--pin-mint)");
    expect(pinColorBg("chartreuse")).toBe("var(--pin-bg)");
    expect(pinColorBg("")).toBe("var(--pin-bg)");
  });
  it("rowToPost keeps a palette colour and drops a non-palette one", () => {
    const ts = "2026-07-13T12:00:00Z";
    expect(rowToPost({ json_metadata: { m: "hi", ts, c: "mint" } })?.color).toBe("mint");
    expect(rowToPost({ json_metadata: { m: "hi", ts, c: "chartreuse" } })?.color).toBe("");
  });
});

describe("theme helpers", () => {
  it("honours a saved choice over the OS preference", () => {
    expect(resolveInitialTheme("dark", false)).toBe("dark");
    expect(resolveInitialTheme("light", true)).toBe("light");
  });
  it("follows the OS preference when there is no saved choice", () => {
    expect(resolveInitialTheme(null, true)).toBe("dark");
    expect(resolveInitialTheme(null, false)).toBe("light");
    expect(resolveInitialTheme("garbage", true)).toBe("dark");
  });
  it("toggles between light and dark", () => {
    expect(nextTheme("light")).toBe("dark");
    expect(nextTheme("dark")).toBe("light");
  });
});
