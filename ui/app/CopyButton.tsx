"use client";

import { useState } from "react";

/** A small button that copies `value` to the clipboard and briefly flashes "copied". */
export function CopyButton({
  value,
  label = "copy",
  title,
}: {
  value: string;
  label?: string;
  title?: string;
}) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      /* clipboard unavailable/blocked - ignore */
    }
  }

  return (
    <button
      type="button"
      onClick={() => void copy()}
      aria-label={title ?? "Copy to clipboard"}
      title={title ?? "Copy to clipboard"}
      style={{ fontSize: 11, fontWeight: 500, padding: "1px 6px", borderRadius: 6 }}
    >
      {copied ? "copied" : label}
    </button>
  );
}
