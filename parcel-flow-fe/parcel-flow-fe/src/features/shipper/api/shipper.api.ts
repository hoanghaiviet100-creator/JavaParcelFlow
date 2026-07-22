import { httpClient } from "@/shared/api/http-client";

export type DeliveryAssignmentStatus =
  | "ASSIGNED"
  | "ACCEPTED"
  | "PICKED_UP"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "FAILED"
  | "RETURNED_TO_HUB"
  | "CANCELLED";

export interface DeliveryAssignmentResponse {
  id: number;
  parcelId: number;
  parcelCode?: string | null;
  parcelStatus?: string | null;
  shipperId: number;
  status: DeliveryAssignmentStatus;
  assignmentType: string;
  assignmentReason?: string | null;
  assignedAt: string;
  acceptedAt?: string | null;
  pickedUpAt?: string | null;
  completedAt?: string | null;
}

export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

/** GET /api/v1/shipper/assignments — the logged-in shipper's own queue. */
export async function getMyAssignmentsApi(): Promise<ApiEnvelope<DeliveryAssignmentResponse[]>> {
  return httpClient.get<ApiEnvelope<DeliveryAssignmentResponse[]>>("/v1/shipper/assignments");
}

/** PATCH /api/v1/shipper/assignments/{id}/status */
export async function updateAssignmentStatusApi(
  id: number,
  status: DeliveryAssignmentStatus
): Promise<ApiEnvelope<DeliveryAssignmentResponse>> {
  return httpClient.patch<ApiEnvelope<DeliveryAssignmentResponse>>(
    `/v1/shipper/assignments/${id}/status`,
    { status }
  );
}
