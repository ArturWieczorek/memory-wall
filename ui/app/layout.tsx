import "./globals.css";
import type { ReactNode } from "react";
import Script from "next/script";

const description =
  "A public, permanent message wall on Cardano - post a short message that lives on-chain forever, shown as a live feed.";

export const metadata = {
  title: "Memory Wall",
  description,
  openGraph: { title: "Memory Wall", description, type: "website" },
  twitter: { card: "summary", title: "Memory Wall", description },
};

// Set the theme AND accent on <html> BEFORE the page paints, so there is no flash of the wrong
// colours. A saved theme wins, else the OS preference; a saved accent (format "#hex|r, g, b") is
// applied as CSS variables. Mirrors the proof-of-existence site's pre-paint script.
const themeInit = `(function(){try{var r=document.documentElement;var t=localStorage.getItem('wall-theme');if(t!=='light'&&t!=='dark'){t=(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches)?'dark':'light';}r.setAttribute('data-theme',t);var a=localStorage.getItem('wall-accent');if(a){var p=a.split('|');if(p.length===2){r.style.setProperty('--accent',p[0]);r.style.setProperty('--accent-rgb',p[1]);}}}catch(e){}})();`;

const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="icon" href={`${basePath}/favicon.svg`} type="image/svg+xml" />
        <script dangerouslySetInnerHTML={{ __html: themeInit }} />
      </head>
      <body>
        {/* Runtime config (backend URL, network). Loaded before the app so it can read window.__WALL_*.
            Prefixed with the base path because public/ files are not auto-prefixed on project Pages. */}
        <Script src={`${basePath}/config.js`} strategy="beforeInteractive" />
        {children}
      </body>
    </html>
  );
}
