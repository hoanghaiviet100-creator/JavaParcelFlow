import { test, expect } from "@playwright/test";
import { login, accessToken, USERS } from "./helpers";

test.describe("authentication", () => {
  test("valid login lands on the dashboard and stores a token", async ({ page }) => {
    await login(page, "admin");
    expect(page.url()).toContain("/dashboard");
    expect(await accessToken(page)).not.toBeNull();
    await expect(page.getByText(/admin@parcelflow\.local/i)).toBeVisible();
  });

  test("wrong password is rejected and shows an error", async ({ page }) => {
    await page.goto("/login");
    await page.locator('input[name="email"]').fill(USERS.admin.email);
    await page.locator('input[name="password"]').fill("WrongPass1!");
    await page.getByRole("button", { name: /sign in/i }).click();

    await expect(page.getByText(/invalid email or password/i)).toBeVisible();
    expect(page.url()).toContain("/login");
    expect(await accessToken(page)).toBeNull();
  });

  test("logout clears the stored tokens and revokes the session", async ({ page }) => {
    await login(page, "staff");
    expect(await accessToken(page)).not.toBeNull();

    await page.getByRole("button", { name: /log out/i }).click();
    await page.waitForURL("**/login");

    // Both the local credential and the ability to reach a protected page are gone.
    expect(await accessToken(page)).toBeNull();
    await page.goto("/orders");
    await page.waitForURL("**/login");
  });

  test("a non-admin cannot reach the admin user pages", async ({ page }) => {
    await login(page, "dispatcher");
    await page.goto("/users");
    // The role guard bounces a dispatcher back to the dashboard.
    await page.waitForURL("**/dashboard");
    await expect(page.getByText(/staff\.hcm@parcelflow\.local/i)).toHaveCount(0);
  });
});
