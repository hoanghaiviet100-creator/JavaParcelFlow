/**
 * Central store for JWT access/refresh tokens.
 *
 * The backend is stateless JWT (not cookie sessions), so the client must hold the
 * tokens and send `Authorization: Bearer <access>`. We keep them in localStorage so a
 * page refresh stays logged in, with an in-memory mirror for SSR safety.
 *
 * NOTE: localStorage is readable by any JS on the page, so this trades some XSS exposure
 * for simplicity. The app already has no cross-origin script includes; if stricter
 * isolation is needed later, move to httpOnly refresh cookies + in-memory access token.
 */
const ACCESS_KEY = "pf_access_token";
const REFRESH_KEY = "pf_refresh_token";

let memoryAccess: string | null = null;
let memoryRefresh: string | null = null;

const isBrowser = typeof window !== "undefined";

export const tokenStore = {
  getAccess(): string | null {
    if (memoryAccess) return memoryAccess;
    if (isBrowser) memoryAccess = window.localStorage.getItem(ACCESS_KEY);
    return memoryAccess;
  },

  getRefresh(): string | null {
    if (memoryRefresh) return memoryRefresh;
    if (isBrowser) memoryRefresh = window.localStorage.getItem(REFRESH_KEY);
    return memoryRefresh;
  },

  set(access: string, refresh: string): void {
    memoryAccess = access;
    memoryRefresh = refresh;
    if (isBrowser) {
      window.localStorage.setItem(ACCESS_KEY, access);
      window.localStorage.setItem(REFRESH_KEY, refresh);
    }
  },

  clear(): void {
    memoryAccess = null;
    memoryRefresh = null;
    if (isBrowser) {
      window.localStorage.removeItem(ACCESS_KEY);
      window.localStorage.removeItem(REFRESH_KEY);
    }
  },

  hasSession(): boolean {
    return !!this.getAccess();
  },
};
