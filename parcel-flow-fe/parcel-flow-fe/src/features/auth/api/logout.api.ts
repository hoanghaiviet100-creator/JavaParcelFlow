import { httpClient } from "@/shared/api/http-client";
import { LogoutResponse } from "../types/auth.types";

export async function logoutApi(): Promise<LogoutResponse> {
  return httpClient.post<LogoutResponse>("/v1/auth/logout");
}
