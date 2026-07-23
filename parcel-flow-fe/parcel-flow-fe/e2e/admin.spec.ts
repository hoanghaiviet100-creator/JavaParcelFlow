import { test, expect } from "@playwright/test";
import { login } from "./helpers";

/**
 * The admin surfaces that were stubs until recently: the user table, the two
 * detail pages, and the account-unlock action that no screen used to reach.
 */
test.describe("admin console", () => {
  test("user list renders real accounts and links to detail", async ({ page }) => {
    await login(page, "admin");
    await page.goto("/users");

    await expect(page.getByText(/existing accounts/i)).toBeVisible();
    await expect(page.getByRole("link", { name: /hub staff hcm/i })).toBeVisible();

    await page.getByRole("link", { name: /hub staff hcm/i }).click();
    await page.waitForURL(/\/users\/\d+$/);
    await expect(page.getByText(/account detail/i)).toBeVisible();
    await expect(page.getByText(/access status/i)).toBeVisible();
  });

  test("hub detail renders from the hub list", async ({ page }) => {
    await login(page, "admin");
    await page.goto("/hubs");
    await page.getByRole("link", { name: /HUB-SG-MAIN/ }).click();
    await page.waitForURL(/\/hubs\/\d+$/);
    await expect(page.getByText(/hub details/i)).toBeVisible();
    // Name shows in both the subtitle and the field grid; either confirms load.
    await expect(page.getByText(/HCMC Main Hub/).first()).toBeVisible();
  });

  test("account detail drives the resend-temp-password action", async ({ page, request }) => {
    // Create a throwaway account so the action touches no demo credential.
    const adminToken = await request
      .post("http://localhost:8080/api/v1/auth/login", {
        data: { email: "admin@parcelflow.local", password: "Admin@12345" },
      })
      .then((r) => r.json())
      .then((j) => j.data.accessToken);

    const email = `e2e.detail.${Date.now()}@parcelflow.local`;
    const createRes = await request.post("http://localhost:8080/api/v1/users", {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { fullName: "E2E Detail Target", email, roleCode: "HUB_STAFF" },
    });
    expect(createRes.status()).toBe(201);
    const userId = (await createRes.json()).data.id;
    expect(userId).toBeTruthy();

    await login(page, "admin");
    await page.goto(`/users/${userId}`);

    await expect(page.getByText(/account detail/i)).toBeVisible();
    // Email shows in both the subtitle and the field grid.
    await expect(page.getByText(email).first()).toBeVisible();
    await expect(page.getByText(/must change password/i).first()).toBeVisible();

    // Resend is always available and hits an endpoint no screen used to reach.
    await page.getByRole("button", { name: /resend temporary password/i }).click();
    await expect(page.getByText(/temporary password was emailed/i)).toBeVisible();
  });
});
