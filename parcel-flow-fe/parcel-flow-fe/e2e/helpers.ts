import { Page, expect } from "@playwright/test";

/** Seeded demo credentials (Flyway V4 / AdminBootstrap). */
export const USERS = {
  admin: { email: "admin@parcelflow.local", password: "Admin@12345", home: "/dashboard" },
  staff: { email: "staff.hcm@parcelflow.local", password: "Staff@12345", home: "/dashboard" },
  dispatcher: { email: "dispatcher@parcelflow.local", password: "Dispatch@12345", home: "/dashboard" },
  shipper: { email: "shipper1@parcelflow.local", password: "Shipper@12345", home: "/shipper/assignments" },
} as const;

/** Drive the real login form and wait for the post-login landing page. */
export async function login(page: Page, who: keyof typeof USERS) {
  const u = USERS[who];
  await page.goto("/login");
  await page.locator('input[name="email"]').fill(u.email);
  await page.locator('input[name="password"]').fill(u.password);
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.waitForURL(`**${u.home}`);
}

/** Read the stored access token the way the app stores it. */
export async function accessToken(page: Page): Promise<string | null> {
  return page.evaluate(() => localStorage.getItem("pf_access_token"));
}

export async function expectSignedIn(page: Page) {
  expect(await accessToken(page)).not.toBeNull();
}
