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

/** One courier a dispatcher may hand a parcel to. */
export interface AssignableShipper {
  userId: number;
  fullName: string;
  hubId: number;
  isAvailable: boolean;
  maxOrdersPerDay: number;
  activeAssignments: number;
}

export interface CreateAssignmentPayload {
  parcelId: number;
  shipperId: number;
  assignmentReason?: string;
}

/**
 * GET /api/v1/delivery-assignments/shippers — couriers available to a dispatcher.
 * Served by the assignment API rather than /v1/users, which is ADMIN-only.
 */
export async function getAssignableShippersApi(): Promise<ApiEnvelope<AssignableShipper[]>> {
  return httpClient.get<ApiEnvelope<AssignableShipper[]>>("/v1/delivery-assignments/shippers");
}

/**
 * POST /api/v1/delivery-assignments — hand a parcel to a courier.
 *
 * Also moves the parcel to ASSIGNED_TO_SHIPPER server-side, so the queue and the
 * parcel status cannot drift apart.
 */
export async function createAssignmentApi(
  payload: CreateAssignmentPayload
): Promise<ApiEnvelope<DeliveryAssignmentResponse>> {
  return httpClient.post<ApiEnvelope<DeliveryAssignmentResponse>>("/v1/delivery-assignments", payload);
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
