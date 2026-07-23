import { defineConfig, devices } from "@playwright/test";

/**
 * E2E config. The specs drive a real browser against a running stack — the
 * frontend on :3000 talking to the backend on :8080 — so they catch exactly the
 * class of defect unit tests miss: broken routes, guards, and end-to-end flows.
 *
 * Point them at another origin with E2E_BASE_URL (CI brings the stack up with
 * docker compose and leaves it at the default). No webServer is configured: the
 * stack is expected to be already running.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [["html", { open: "never" }], ["list"]] : "list",
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
});
