import { httpClient } from "@/shared/api/http-client";
import { ApiEnvelope, ChangePasswordRequest } from "../types/auth.types";

export async function changePasswordApi(
  payload: ChangePasswordRequest
): Promise<ApiEnvelope<null>> {
  // Public endpoint (used during the forced temp-password change), no bearer needed.
  return httpClient.post<ApiEnvelope<null>>("/v1/auth/change-password", payload, {
    skipAuth: true,
  });
}
