import { httpClient } from "@/shared/api/http-client";

export interface HubResponse {
  id: number;
  code: string;
  name: string;
  type: string;
  phone?: string | null;
  addressLine: string;
  wardId?: number | null;
  districtId: number;
  provinceId: number;
  parentHubId?: number | null;
  isActive: boolean;
}

export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

/** GET /api/v1/hubs — full hub registry (small list). */
export async function listHubsApi(): Promise<ApiEnvelope<HubResponse[]>> {
  return httpClient.get<ApiEnvelope<HubResponse[]>>("/v1/hubs");
}

/** GET /api/v1/hubs/{id} */
export async function getHubApi(id: number | string): Promise<ApiEnvelope<HubResponse>> {
  return httpClient.get<ApiEnvelope<HubResponse>>(`/v1/hubs/${id}`);
}
