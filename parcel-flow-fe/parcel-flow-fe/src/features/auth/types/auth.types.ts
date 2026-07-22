import { UserProfile } from "@/types/domain/user";

/** Standard backend envelope: { success, message, data, timestamp, ... } */
export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/** Backend LoginResponse record: accessToken, refreshToken, tokenType, expiresInSeconds, role */
export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  role: string;
}

export type LoginResponse = ApiEnvelope<LoginResult>;

export interface ChangePasswordRequest {
  email: string;
  currentPassword: string;
  newPassword: string;
}

/** Backend /v1/auth/me returns AuthPrincipal { userId, email, role }. */
export interface AuthPrincipalDto {
  userId: number;
  email: string;
  role: string;
}

export type GetProfileResponse = ApiEnvelope<AuthPrincipalDto>;

export type LogoutResponse = ApiEnvelope<null>;

/** Maps the minimal principal from /me into the richer UserProfile the store expects. */
export function principalToProfile(p: AuthPrincipalDto): UserProfile {
  return {
    id: String(p.userId),
    email: p.email,
    fullName: p.email,
    role: p.role as UserProfile["role"],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
}
