import type { ReactNode } from "react";
import Script from "next/script";

export const metadata = {
  title: "Memory Wall",
  description: "A public, permanent message wall on Cardano",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, sans-serif", maxWidth: 640, margin: "2rem auto" }}>
        {/* Runtime config (backend URL, network). Loaded before the app so it can read window.__WALL_*.
            Prefixed with the base path because public/ files are not auto-prefixed on project Pages. */}
        <Script
          src={`${process.env.NEXT_PUBLIC_BASE_PATH ?? ""}/config.js`}
          strategy="beforeInteractive"
        />
        {children}
      </body>
    </html>
  );
}
