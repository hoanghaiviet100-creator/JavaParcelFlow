import { httpClient } from "@/shared/api/http-client";
import { GetTrackingResponse } from "../types/tracking.types";

export async function getTrackingApi(code: string, phone?: string): Promise<GetTrackingResponse> {
  const params: Record<string, string | undefined> = { phone };
  return httpClient.get<GetTrackingResponse>(`/tracking/${code}`, { params });
}
