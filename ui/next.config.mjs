// Next.js config. Two modes:
//
//  - dev (`next dev`): a proxy forwards /api/* to the local Java backend, so the browser can call it
//    same-origin. This is a convenience for local development only.
//
//  - prod (`next build`): a fully STATIC export (plain HTML/JS/CSS) for GitHub Pages or Cloudflare
//    Pages. No Next.js server runs in production, so the browser calls the backend DIRECTLY via
//    window.__WALL_API__ (see public/config.js) - the dev proxy is neither present nor needed.
//    A static export forbids rewrites(), which is why the proxy is guarded to dev only.
//
// WALL_BASE_PATH: set to "/<repo>" (e.g. "/memory-wall") when hosting on GitHub *project* Pages,
// where the site lives under a sub-path. Leave empty for root hosting (Cloudflare Pages, a custom
// domain, or a <user>.github.io site). NEXT_PUBLIC_BASE_PATH mirrors it so the client can build the
// correct URL for public/config.js (files in public/ are NOT auto-prefixed by basePath).
const isProd = process.env.NODE_ENV === "production";
const backend = process.env.WALL_BACKEND || "http://localhost:8090";
const basePath = process.env.WALL_BASE_PATH || "";

/** @type {import('next').NextConfig} */
const common = {
  images: { unoptimized: true }, // a static export has no Image Optimization server
  env: { NEXT_PUBLIC_BASE_PATH: basePath },
  ...(basePath ? { basePath, assetPrefix: basePath } : {}),
};

export default isProd
  ? { ...common, output: "export" }
  : {
      ...common,
      async rewrites() {
        return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
      },
    };
