import { httpClient } from "@/shared/api/http-client";
import { ApiEnvelope } from "@/features/auth/types/auth.types";

/** Mirrors com.parcelflow.auth.dto.UserResponse — never carries the password hash. */
export interface UserResponse {
  id: number;
  fullName: string;
  email: string;
  phone?: string | null;
  roleCode?: string | null;
  hubId?: number | null;
  active: boolean;
  mustChangePassword: boolean;
  passwordExpiresAt?: string | null;
  lockReason?: string | null;
  lockedAt?: string | null;
  createdAt?: string | null;
}

export interface PagedUsers {
  content: UserResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** GET /api/v1/users — ADMIN only. */
export async function listUsersApi(page = 0, size = 20): Promise<ApiEnvelope<PagedUsers>> {
  return httpClient.get<ApiEnvelope<PagedUsers>>("/v1/users", { params: { page, size } });
}

/** GET /api/v1/users/{id} — ADMIN only. */
export async function getUserApi(id: number | string): Promise<ApiEnvelope<UserResponse>> {
  return httpClient.get<ApiEnvelope<UserResponse>>(`/v1/users/${id}`);
}

/**
 * POST /api/v1/users/{id}/unlock — clears a permanent lock and revokes any
 * live session. The endpoint has existed since the lockout work landed; until
 * now no screen called it, so an account locked by repeated failed logins could
 * only be freed with a direct API call.
 */
export async function unlockUserApi(id: number | string): Promise<ApiEnvelope<null>> {
  return httpClient.post<ApiEnvelope<null>>(`/v1/users/${id}/unlock`);
}

/** POST /api/v1/users/{id}/resend-temp-password — also previously unreachable from the UI. */
export async function resendTempPasswordApi(id: number | string): Promise<ApiEnvelope<null>> {
  return httpClient.post<ApiEnvelope<null>>(`/v1/users/${id}/resend-temp-password`);
}
