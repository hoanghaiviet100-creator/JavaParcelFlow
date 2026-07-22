import { httpClient } from "@/shared/api/http-client";
import { GetProfileResponse } from "../types/auth.types";

export async function getProfileApi(): Promise<GetProfileResponse> {
  return httpClient.get<GetProfileResponse>("/v1/auth/me");
}
