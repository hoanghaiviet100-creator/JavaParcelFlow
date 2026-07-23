import { test, expect } from "@playwright/test";
import { login } from "./helpers";

/**
 * The whole point of these is to catch what unit tests cannot: that clicking
 * through the app actually works. The create-order redirect is the case that
 * was silently broken (it pushed to /dashboard/orders/<id>, a route-group path
 * that 404s), so it is asserted explicitly.
 */
test.describe("order lifecycle", () => {
  test("create an order through the form and land on its detail page", async ({ page }) => {
    await login(page, "staff");
    await page.goto("/orders/create");

    await page.locator('select[name="createdHubId"]').selectOption("1");
    await page.locator('select[name="finalHubId"]').selectOption("2");
    await page.locator('select[name="serviceType"]').selectOption("EXPRESS");
    await page.locator('select[name="paymentType"]').selectOption("COD");
    await page.locator('input[name="codAmount"]').fill("450000");

    await page.locator('input[name="sender.fullName"]').fill("Shop ABC");
    await page.locator('input[name="sender.phone"]').fill("0912345678");
    await page.locator('input[name="sender.addressLine"]').fill("120 Le Loi");
    await page.locator('input[name="sender.districtId"]').fill("1");
    await page.locator('input[name="sender.provinceId"]').fill("1");

    await page.locator('input[name="receiver.fullName"]').fill("Nguyen Thi Hoa");
    await page.locator('input[name="receiver.phone"]').fill("0987654321");
    await page.locator('input[name="receiver.addressLine"]').fill("55 Nguyen Thi Thap");
    await page.locator('input[name="receiver.districtId"]').fill("3");
    await page.locator('input[name="receiver.provinceId"]').fill("1");

    await page.locator('input[name="parcels.0.weight"]').fill("1.5");

    await page.getByRole("button", { name: /register order/i }).click();

    // Redirect must reach a real order detail route, not the 404 page.
    await page.waitForURL(/\/orders\/\d+$/);
    await expect(page.getByText(/order manifest details/i)).toBeVisible();
    // The code appears in both the header and the timeline copy; either proves it.
    await expect(page.getByText(/OD\d{8}/).first()).toBeVisible();
    await expect(page.getByText("Shop ABC")).toBeVisible();
  });

  test("public tracking gates PII behind the receiver phone", async ({ page, request }) => {
    // Seed an order via the API so the test owns a known code, then exercise the
    // public page — which needs no login.
    const loginRes = await request.post("http://localhost:8080/api/v1/auth/login", {
      data: { email: "staff.hcm@parcelflow.local", password: "Staff@12345" },
    });
    const token = (await loginRes.json()).data.accessToken;

    const orderRes = await request.post("http://localhost:8080/api/v1/orders", {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        createdHubId: 1,
        sender: { fullName: "Tracking Sender", phone: "0912000000", addressLine: "1 A St", districtId: 1, provinceId: 1 },
        receiver: { fullName: "Tracking Receiver", phone: "0987654321", addressLine: "2 B St", districtId: 3, provinceId: 1 },
        parcels: [{ weight: 1 }],
      },
    });
    const orderCode = (await orderRes.json()).data.orderCode;

    // No phone: status visible, names withheld.
    await page.goto(`/tracking/result?code=${orderCode}`);
    await expect(page.getByText(orderCode).first()).toBeVisible();
    await expect(page.getByText("Tracking Sender")).toHaveCount(0);

    // Correct phone: names revealed.
    await page.goto(`/tracking/result?code=${orderCode}&phone=0987654321`);
    await expect(page.getByText("Tracking Sender")).toBeVisible();
    await expect(page.getByText("Tracking Receiver")).toBeVisible();
  });
});
