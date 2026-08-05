import { httpClient } from "@/shared/api/http-client";
import {
  ApiEnvelope,
  PageResponse,
  ParcelResponse,
  ParcelStatus,
} from "@/features/orders/types/order.types";

export type { ParcelResponse, ParcelStatus };

export interface UpdateParcelStatusPayload {
  status: ParcelStatus;
  hubId?: number;
  note?: string;
}

/**
 * What the server will actually accept as this parcel's next status.
 *
 * `corrections` are supervisor-only repairs out of a final status (e.g. undoing a
 * mis-scanned CANCELLED). It comes back empty for roles that may not perform them,
 * so the caller can render whatever it is given without re-checking the role.
 */
export interface ParcelTransitionsResponse {
  current: ParcelStatus;
  allowed: ParcelStatus[];
  corrections: ParcelStatus[];
}

/** GET /api/v1/parcels?page=&size= */
export async function listParcelsApi(
  page = 0,
  size = 20
): Promise<ApiEnvelope<PageResponse<ParcelResponse>>> {
  return httpClient.get<ApiEnvelope<PageResponse<ParcelResponse>>>("/v1/parcels", {
    params: { page, size },
  });
}

/** GET /api/v1/parcels/{id} */
export async function getParcelApi(id: number | string): Promise<ApiEnvelope<ParcelResponse>> {
  return httpClient.get<ApiEnvelope<ParcelResponse>>(`/v1/parcels/${id}`);
}

/** GET /api/v1/parcels/by-code/{code} — used by the terminal scan page. */
export async function getParcelByCodeApi(code: string): Promise<ApiEnvelope<ParcelResponse>> {
  return httpClient.get<ApiEnvelope<ParcelResponse>>(`/v1/parcels/by-code/${encodeURIComponent(code)}`);
}

/** GET /api/v1/parcels/{id}/transitions */
export async function getParcelTransitionsApi(
  id: number | string
): Promise<ApiEnvelope<ParcelTransitionsResponse>> {
  return httpClient.get<ApiEnvelope<ParcelTransitionsResponse>>(`/v1/parcels/${id}/transitions`);
}

/** PATCH /api/v1/parcels/{id}/status */
export async function updateParcelStatusApi(
  id: number | string,
  payload: UpdateParcelStatusPayload
): Promise<ApiEnvelope<ParcelResponse>> {
  return httpClient.patch<ApiEnvelope<ParcelResponse>>(`/v1/parcels/${id}/status`, payload);
}
