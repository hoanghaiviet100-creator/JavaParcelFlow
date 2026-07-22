import { httpClient } from "@/shared/api/http-client";
import { LoginRequest, LoginResponse } from "../types/auth.types";

export async function loginApi(payload: LoginRequest): Promise<LoginResponse> {
  // Backend endpoint: POST /api/v1/auth/login ; login must not send a stale bearer token.
  return httpClient.post<LoginResponse>("/v1/auth/login", payload, { skipAuth: true });
}
