import { httpClient } from "@/shared/api/http-client";
import { ApiEnvelope } from "@/features/auth/types/auth.types";

/** Mirrors com.parcelflow.logistics.dto.StatsResponse. */
export interface StatsOverview {
  totalUsers: number;
  activeHubs: number;
  totalOrders: number;
  ordersToday: number;
  totalParcels: number;
  parcelsInboundPending: number;
  parcelsWaitingForRoute: number;
  parcelsInTransit: number;
  pendingDeliveries: number;
  openRoutePlans: number;
  openAssignments: number;
}

/** GET /api/v1/stats/overview — live dashboard counts. */
export async function getStatsOverviewApi(): Promise<ApiEnvelope<StatsOverview>> {
  return httpClient.get<ApiEnvelope<StatsOverview>>("/v1/stats/overview");
}
