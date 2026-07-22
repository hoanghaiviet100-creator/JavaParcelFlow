/** Backend envelope: { success, message, data, timestamp, ... } */
export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

/**
 * Mirrors the backend `PageResponse` exactly:
 * { content, page, size, totalElements, totalPages }.
 * (Note: NOT the older ApiPaginatedResponse shape — that never matched the API.)
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type OrderStatus =
  | "CREATED"
  | "RECEIVED_AT_ORIGIN_HUB"
  | "WAITING_FOR_ROUTE"
  | "IN_TRANSIT"
  | "ARRIVED_AT_FINAL_HUB"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "DELIVERY_FAILED"
  | "RETURNING"
  | "RETURNED"
  | "CANCELLED";

export type ParcelStatus =
  | "CREATED"
  | "RECEIVED_AT_ORIGIN_HUB"
  | "WAITING_FOR_ROUTE"
  | "WAITING_FOR_OUTBOUND"
  | "IN_TRANSIT"
  | "ARRIVED_AT_HUB"
  | "READY_FOR_DELIVERY"
  | "ASSIGNED_TO_SHIPPER"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "DELIVERY_FAILED"
  | "RETURNING"
  | "RETURNED"
  | "LOST"
  | "DAMAGED"
  | "CANCELLED";

export type ServiceType = "STANDARD" | "EXPRESS" | "ECONOMY";
export type PaymentType = "SENDER_PAY" | "RECEIVER_PAY" | "COD";

export interface OrderPartyResponse {
  id: number;
  partyType: string;
  fullName: string;
  phone: string;
  email?: string | null;
  addressLine: string;
  wardId?: number | null;
  districtId?: number | null;
  provinceId?: number | null;
}

export interface ParcelResponse {
  id: number;
  parcelCode: string;
  categoryId?: number | null;
  weight: number;
  status: ParcelStatus;
}

export interface OrderResponse {
  id: number;
  orderCode: string;
  status: OrderStatus;
  serviceType?: ServiceType | null;
  paymentType?: PaymentType | null;
  totalWeight?: number | null;
  totalFee?: number | null;
  codAmount?: number | null;
  note?: string | null;
  createdHubId?: number | null;
  currentHubId?: number | null;
  finalHubId?: number | null;
  createdBy?: number | null;
  createdAt: string;
  sender: OrderPartyResponse;
  receiver: OrderPartyResponse;
  parcels: ParcelResponse[];
}

export interface OrderSummaryResponse {
  id: number;
  orderCode: string;
  status: OrderStatus;
  totalWeight?: number | null;
  codAmount?: number | null;
  createdAt: string;
}

export interface TrackingEventResponse {
  id: number;
  status: string;
  title: string;
  message: string;
  hubId?: number | null;
  visibleToCustomer: boolean;
  createdAt: string;
}

/** ----- Request payloads (match CreateOrderRequest / UpdateOrderRequest) ----- */

export interface PartyRequest {
  fullName: string;
  phone: string;
  email?: string;
  addressLine: string;
  wardId?: number;
  districtId: number;
  provinceId: number;
  latitude?: number;
  longitude?: number;
}

export interface ParcelRequestPayload {
  categoryId?: number;
  weight: number;
  length?: number;
  width?: number;
  height?: number;
  declaredValue?: number;
  note?: string;
}

export interface CreateOrderPayload {
  createdHubId: number;
  finalHubId?: number;
  serviceType?: ServiceType;
  paymentType?: PaymentType;
  codAmount?: number;
  note?: string;
  sender: PartyRequest;
  receiver: PartyRequest;
  parcels: ParcelRequestPayload[];
}

export interface UpdateOrderPayload {
  note?: string;
  serviceType?: ServiceType;
  paymentType?: PaymentType;
  finalHubId?: number;
  codAmount?: number;
}
