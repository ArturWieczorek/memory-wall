import type { ReactNode } from "react";

export const metadata = {
  title: "Memory Wall",
  description: "A public, permanent message wall on Cardano",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, sans-serif", maxWidth: 640, margin: "2rem auto" }}>
        {children}
      </body>
    </html>
  );
}
