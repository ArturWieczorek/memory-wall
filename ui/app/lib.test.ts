import { describe, it, expect } from "vitest";
import {
  byteLength,
  explorerTxUrl,
  relativeTime,
  rowToPost,
  resolveInitialTheme,
  nextTheme,
  MAX_MESSAGE_BYTES,
} from "./lib";

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
    });
  });
  it("accepts a plain string message and a missing tx hash", () => {
    const post = rowToPost({ json_metadata: { a: "", m: "hi", ts: "2026-07-13T12:00:00Z" } });
    expect(post).toEqual({ author: "", message: "hi", timestamp: "2026-07-13T12:00:00Z", txHash: "" });
  });
  it("returns null for malformed rows", () => {
    expect(rowToPost({ json_metadata: { a: "x", ts: "2026-07-13T12:00:00Z" } })).toBeNull(); // no message
    expect(rowToPost({ json_metadata: { m: "hi" } })).toBeNull(); // no timestamp
    expect(rowToPost({ json_metadata: "nope" })).toBeNull(); // not an object
    expect(rowToPost({})).toBeNull(); // no metadata
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
