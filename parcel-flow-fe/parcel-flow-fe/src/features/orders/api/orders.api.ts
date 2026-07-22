import { httpClient } from "@/shared/api/http-client";
import {
  ApiEnvelope,
  CreateOrderPayload,
  OrderResponse,
  OrderSummaryResponse,
  PageResponse,
  TrackingEventResponse,
  UpdateOrderPayload,
} from "../types/order.types";

/** GET /api/v1/orders?page=&size= — paged list of order summaries. */
export async function listOrdersApi(
  page = 0,
  size = 20
): Promise<ApiEnvelope<PageResponse<OrderSummaryResponse>>> {
  return httpClient.get<ApiEnvelope<PageResponse<OrderSummaryResponse>>>("/v1/orders", {
    params: { page, size },
  });
}

/** GET /api/v1/orders/{id} — full order detail. */
export async function getOrderApi(id: number | string): Promise<ApiEnvelope<OrderResponse>> {
  return httpClient.get<ApiEnvelope<OrderResponse>>(`/v1/orders/${id}`);
}

/** POST /api/v1/orders — create an order. */
export async function createOrderApi(
  payload: CreateOrderPayload
): Promise<ApiEnvelope<OrderResponse>> {
  return httpClient.post<ApiEnvelope<OrderResponse>>("/v1/orders", payload);
}

/** PUT /api/v1/orders/{id} — update mutable order fields. */
export async function updateOrderApi(
  id: number | string,
  payload: UpdateOrderPayload
): Promise<ApiEnvelope<OrderResponse>> {
  return httpClient.put<ApiEnvelope<OrderResponse>>(`/v1/orders/${id}`, payload);
}

/** DELETE /api/v1/orders/{id} — cancel an order. */
export async function cancelOrderApi(id: number | string): Promise<ApiEnvelope<null>> {
  return httpClient.delete<ApiEnvelope<null>>(`/v1/orders/${id}`);
}

/** GET /api/v1/orders/{id}/tracking-events — internal tracking timeline. */
export async function getOrderTrackingEventsApi(
  id: number | string
): Promise<ApiEnvelope<TrackingEventResponse[]>> {
  return httpClient.get<ApiEnvelope<TrackingEventResponse[]>>(`/v1/orders/${id}/tracking-events`);
}
