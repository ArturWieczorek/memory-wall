import "./globals.css";
import type { ReactNode } from "react";
import Script from "next/script";

export const metadata = {
  title: "Memory Wall",
  description: "A public, permanent message wall on Cardano",
};

// Set the theme on <html> BEFORE the page paints, so there is no light-then-dark flash. A saved
// choice (localStorage) wins; otherwise follow the OS preference. Mirrors resolveInitialTheme().
const themeInit = `(function(){try{var t=localStorage.getItem('wall-theme');if(t!=='light'&&t!=='dark'){t=(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches)?'dark':'light';}document.documentElement.setAttribute('data-theme',t);}catch(e){}})();`;

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInit }} />
      </head>
      <body style={{ fontFamily: "system-ui, sans-serif", maxWidth: 640, margin: "2rem auto", padding: "0 1rem" }}>
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
