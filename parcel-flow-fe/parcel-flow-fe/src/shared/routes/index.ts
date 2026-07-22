import { PUBLIC_ROUTES } from "./public-routes";
import { AUTH_ROUTES } from "./auth-routes";
import { DASHBOARD_ROUTES } from "./dashboard-routes";
import { SHIPPER_ROUTES } from "./shipper-routes";

export { PUBLIC_ROUTES, AUTH_ROUTES, DASHBOARD_ROUTES, SHIPPER_ROUTES };

export const APP_ROUTES = {
  ...PUBLIC_ROUTES,
  ...AUTH_ROUTES,
  ...DASHBOARD_ROUTES,
  ...SHIPPER_ROUTES,
} as const;
