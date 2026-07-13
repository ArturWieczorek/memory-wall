import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Unit/component tests for the UI. Pure helpers (app/lib.ts) plus React components via jsdom.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["app/**/*.test.{ts,tsx}"],
  },
});
