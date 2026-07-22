import { httpClient } from "@/shared/api/http-client";
import { ApiEnvelope } from "@/features/auth/types/auth.types";

export interface CreateUserRequest {
  fullName: string;
  email: string;
  phone?: string;
  roleCode: string;
  hubId?: number;
}

export interface CreateUserResult {
  id: number;
  email: string;
  fullName: string;
  roleCode: string;
  hubId?: number;
  mustChangePassword: boolean;
}

export async function createUserApi(
  payload: CreateUserRequest
): Promise<ApiEnvelope<CreateUserResult>> {
  // Admin-only endpoint; requires a valid ADMIN bearer token.
  return httpClient.post<ApiEnvelope<CreateUserResult>>("/v1/users", payload);
}
