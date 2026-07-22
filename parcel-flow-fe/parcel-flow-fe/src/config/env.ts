/**
 * Application environment configuration.
 * All access to process.env must be centralized here.
 */
export const env = {
  appName: process.env.NEXT_PUBLIC_APP_NAME ?? "Parcel Flow",
  // Backend base URL (Spring Boot). Override with NEXT_PUBLIC_API_BASE_URL in deployment.
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
  // Backend mounts all endpoints under /api ; controllers add /v1/...
  apiPrefix: process.env.NEXT_PUBLIC_API_PREFIX ?? "api",
  // MSW is now OFF by default — the app talks to the real backend.
  enableMSW: process.env.NEXT_PUBLIC_ENABLE_MSW === "true",
} as const;
