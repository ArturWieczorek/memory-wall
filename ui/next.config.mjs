// Proxy /api/* to the Java backend so the browser can call it same-origin.
const backend = process.env.WALL_BACKEND || "http://localhost:8090";
export default {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};
