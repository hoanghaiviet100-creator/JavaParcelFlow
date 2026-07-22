import { env } from "@/config/env";
import { ApiError } from "./api-error";
import { tokenStore } from "./token-store";

interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | undefined>;
  /** Skip attaching the Authorization header (used by login/refresh). */
  skipAuth?: boolean;
  /** Internal: prevents infinite refresh recursion. */
  _isRetry?: boolean;
}

function buildUrl(endpoint: string, params?: RequestOptions["params"]): string {
  let queryString = "";
  if (params) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        searchParams.append(key, String(value));
      }
    });
    const paramString = searchParams.toString();
    if (paramString) queryString = `?${paramString}`;
  }
  const baseUrl = env.apiBaseUrl.replace(/\/$/, "");
  const prefix = env.apiPrefix.startsWith("/") ? env.apiPrefix : `/${env.apiPrefix}`;
  const path = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
  return `${baseUrl}${prefix}${path}${queryString}`;
}

/**
 * In-flight refresh, shared by every caller that hits a 401 at the same time.
 *
 * Without this each 401 started its own refresh with the same refresh token.
 * The server rotates on refresh, so the extra calls raced: one of them won and
 * the others were handed access tokens whose jti was no longer the active one,
 * leaving their retried request to fail with a second 401 that the retry guard
 * will not attempt to recover from. Any page issuing two queries in parallel
 * could therefore break the moment the access token expired.
 */
let refreshInFlight: Promise<boolean> | null = null;

/** Attempt a one-time silent refresh; returns true if a new access token was obtained. */
function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = tokenStore.getRefresh();
  if (!refreshToken) return false;
  try {
    const res = await fetch(buildUrl("/v1/auth/refresh"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const json = (await res.json()) as { data?: { accessToken?: string; refreshToken?: string } };
    const access = json?.data?.accessToken;
    const refresh = json?.data?.refreshToken;
    if (access && refresh) {
      tokenStore.set(access, refresh);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { params, headers, skipAuth, _isRetry, ...customConfig } = options;
  const fullUrl = buildUrl(endpoint, params);

  const authHeaders: Record<string, string> = {};
  if (!skipAuth) {
    const token = tokenStore.getAccess();
    if (token) authHeaders.Authorization = `Bearer ${token}`;
  }

  const config: RequestInit = {
    method: options.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders,
      ...headers,
    },
    ...customConfig,
  };

  try {
    const response = await fetch(fullUrl, config);

    if (response.status === 204) return {} as T;

    let responseData: Record<string, unknown>;
    const contentType = response.headers.get("content-type");
    if (contentType && contentType.includes("application/json")) {
      responseData = (await response.json()) as Record<string, unknown>;
    } else {
      responseData = { message: await response.text() };
    }

    if (!response.ok) {
      // Backend failure envelope: { success:false, message, error:{ code, message, details }, path }
      const errorBody = (responseData.error ?? {}) as Record<string, unknown>;
      const code = errorBody.code ?? responseData.code;
      const details = (errorBody.details ?? responseData.details) as
        | Record<string, string[]>
        | string[]
        | undefined;

      // Transparent refresh on 401 (expired access token), once.
      if (response.status === 401 && !skipAuth && !_isRetry) {
        const refreshed = await tryRefresh();
        if (refreshed) {
          return request<T>(endpoint, { ...options, _isRetry: true });
        }
        tokenStore.clear();
      }
      throw new ApiError(response.status, {
        message: String(
          errorBody.message || responseData.message || response.statusText || "Something went wrong"
        ),
        code: code ? String(code) : undefined,
        details: Array.isArray(details)
          ? { _errors: details }
          : (details as Record<string, string[]> | undefined),
        timestamp: responseData.timestamp ? String(responseData.timestamp) : undefined,
      });
    }

    return responseData as unknown as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(500, {
      message: error instanceof Error ? error.message : "Network request failed",
      timestamp: new Date().toISOString(),
    });
  }
}

export const httpClient = {
  get: <T>(endpoint: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(endpoint, { ...options, method: "GET" }),

  post: <T>(endpoint: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(endpoint, {
      ...options,
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    }),

  put: <T>(endpoint: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(endpoint, {
      ...options,
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
    }),

  patch: <T>(endpoint: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(endpoint, {
      ...options,
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
    }),

  delete: <T>(endpoint: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(endpoint, { ...options, method: "DELETE" }),

  request,
};
